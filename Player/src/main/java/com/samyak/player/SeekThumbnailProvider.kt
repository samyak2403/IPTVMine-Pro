package com.samyak.player

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.Executors

/**
 * Frame extractor that feeds thumbnails into the [com.samyak.iptvminetimebar.YouTubeTimeBarPreview]
 * while the user scrubs.
 *
 * To avoid the per-seek decode lag, frames are **prefetched** in the background across the whole
 * duration as soon as playback is ready and cached by time bucket. Scrubbing then reads straight
 * from the cache (instant). A separate on-demand path covers buckets the prefetch hasn't reached
 * yet, and a [fallback] (the currently rendered video frame) keeps the box populated meanwhile.
 *
 * Two independent [MediaMetadataRetriever] instances are used (one for prefetch, one for on-demand)
 * because a single retriever is not safe to call from multiple threads.
 */
class SeekThumbnailProvider {

    private val onDemandExecutor = Executors.newSingleThreadExecutor()
    private val prefetchExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Memory-bounded cache keyed by bucket. Sized by total bitmap bytes so a long movie can't OOM.
    private val cache = object : LruCache<Long, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }

    private var onDemandRetriever: MediaMetadataRetriever? = null
    private var onDemandReady = false
    private var onDemandFailed = false

    private var currentUrl: String? = null
    private var headers: Map<String, String>? = null

    @Volatile
    private var released = false

    @Volatile
    private var prefetchStarted = false

    /**
     * Points the provider at a new media source. Clears cached frames and resets prefetch state.
     */
    fun setSource(url: String?, headers: Map<String, String>? = null) {
        if (released) return
        if (url == currentUrl && headers == this.headers) return

        currentUrl = url
        this.headers = headers
        cache.evictAll()
        onDemandReady = false
        onDemandFailed = false
        prefetchStarted = false

        val old = onDemandRetriever
        onDemandRetriever = null
        onDemandExecutor.execute {
            try {
                old?.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Kicks off a one-time background pass that extracts and caches a thumbnail grid across the
     * whole [durationMs]. Safe to call repeatedly; it only runs once per source.
     */
    fun startPrefetch(durationMs: Long, intervalMs: Long) {
        if (released || prefetchStarted) return
        if (durationMs <= 0 || intervalMs <= 0) return
        val url = currentUrl
        if (url.isNullOrBlank()) return

        prefetchStarted = true
        prefetchExecutor.execute {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                val h = headers
                if (!h.isNullOrEmpty()) retriever.setDataSource(url, h)
                else retriever.setDataSource(url, HashMap())

                var pos = 0L
                var consecutiveFailures = 0
                while (pos <= durationMs && !released) {
                    val bucket = pos / intervalMs
                    if (cache.get(bucket) == null) {
                        try {
                            val frame = retriever.getFrameAtTime(
                                pos * 1000L,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                            )
                            if (frame != null) {
                                cache.put(bucket, scaleDown(frame))
                                consecutiveFailures = 0
                            } else {
                                consecutiveFailures++
                            }
                        } catch (_: Exception) {
                            consecutiveFailures++
                        }
                        // Source can't yield frames (e.g. HLS/live): stop wasting work/bandwidth.
                        if (consecutiveFailures >= MAX_PREFETCH_FAILURES) break
                    }
                    pos += intervalMs
                }
            } catch (e: Exception) {
                Log.d(TAG, "Thumbnail prefetch unavailable: ${e.message}")
            } finally {
                try {
                    retriever?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Loads the frame closest to [positionMs] into [imageView]. Cache hits are instant; otherwise a
     * background decode is attempted and [fallback] is invoked immediately so the box isn't empty.
     */
    fun loadThumbnail(
        imageView: ImageView,
        positionMs: Long,
        intervalMs: Long = 10_000L,
        fallback: ((ImageView) -> Unit)? = null
    ) {
        if (released) return

        val bucket = if (intervalMs > 0) positionMs / intervalMs else positionMs

        cache.get(bucket)?.let {
            imageView.setImageBitmap(it)
            return
        }

        // Immediate placeholder (current rendered frame) while we decode the precise frame.
        fallback?.invoke(imageView)

        if (onDemandFailed || currentUrl.isNullOrBlank()) return

        onDemandExecutor.execute {
            if (released) return@execute
            cache.get(bucket)?.let { cached ->
                mainHandler.post { if (!released) imageView.setImageBitmap(cached) }
                return@execute
            }
            try {
                ensureOnDemandSource()
                val r = onDemandRetriever ?: return@execute

                val frame = r.getFrameAtTime(
                    positionMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: return@execute

                val scaled = scaleDown(frame)
                cache.put(bucket, scaled)

                mainHandler.post {
                    if (!released) imageView.setImageBitmap(scaled)
                }
            } catch (e: Exception) {
                onDemandFailed = true
                Log.d(TAG, "On-demand thumbnail unavailable: ${e.message}")
            }
        }
    }

    private fun ensureOnDemandSource() {
        if (onDemandReady || onDemandFailed) return
        val url = currentUrl ?: run { onDemandFailed = true; return }

        val r = MediaMetadataRetriever()
        val h = headers
        if (!h.isNullOrEmpty()) r.setDataSource(url, h)
        else r.setDataSource(url, HashMap())
        onDemandRetriever = r
        onDemandReady = true
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val maxWidth = THUMB_WIDTH
        if (bitmap.width <= maxWidth) return bitmap
        val ratio = maxWidth.toFloat() / bitmap.width
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return try {
            val scaled = Bitmap.createScaledBitmap(bitmap, maxWidth, height, true)
            if (scaled != bitmap) bitmap.recycle()
            scaled
        } catch (e: Exception) {
            bitmap
        }
    }

    fun release() {
        released = true
        cache.evictAll()
        val r = onDemandRetriever
        onDemandRetriever = null
        try {
            onDemandExecutor.execute {
                try {
                    r?.release()
                } catch (_: Exception) {
                }
            }
            onDemandExecutor.shutdown()
            prefetchExecutor.shutdownNow()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "SeekThumbnailProvider"
        private const val THUMB_WIDTH = 240
        private const val CACHE_BYTES = 32 * 1024 * 1024 // 32 MB of decoded thumbnails
        private const val MAX_PREFETCH_FAILURES = 4
    }
}
