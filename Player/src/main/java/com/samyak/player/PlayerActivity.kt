package com.samyak.player

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.media.AudioManager
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.samyak.doubletapplayerview.DoubleTapPlayerView
import com.samyak.doubletapplayerview.youtube.YouTubeOverlay
import com.samyak.player.R
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL


@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: DoubleTapPlayerView
    private lateinit var youtubeOverlay: YouTubeOverlay
    private lateinit var progressBar: ProgressBar
    private lateinit var errorTextView: TextView

    // Brightness and volume gesture controls
    private lateinit var brightnessControlPanel: LinearLayout
    private lateinit var brightnessProgressBar: ProgressBar
    private lateinit var brightnessText: TextView
    private lateinit var volumeControlPanel: LinearLayout
    private lateinit var volumeProgressBar: ProgressBar
    private lateinit var volumeText: TextView
    private lateinit var swipeGestureDetector: GestureDetector
    private var volumeAccumulator = 0f
    private var isScrolling = false
    private val hidePanelsHandler = Handler(Looper.getMainLooper())
    private val hidePanelsRunnable = Runnable {
        brightnessControlPanel.visibility = View.GONE
        volumeControlPanel.visibility = View.GONE
    }
    private lateinit var backBtn: ImageButton
    private lateinit var playPauseBtn: ImageButton
    private lateinit var fullScreenBtn: ImageButton
    private lateinit var repeatBtn: ImageButton
    private lateinit var prevBtn: ImageButton
    private lateinit var nextBtn: ImageButton
    private lateinit var nextEpisodeBtn: ImageButton
    private lateinit var nextEpisodeOverlay: LinearLayout
    private lateinit var videoTitle: TextView
    private lateinit var exoLiveText: TextView
    private lateinit var topController: LinearLayout
    private lateinit var bottomController: LinearLayout
    private lateinit var audioTrackBtn: ImageButton
    private lateinit var subtitleBtn: ImageButton

    private var channelName: String? = null
    private var channelStreamUrl: String? = null
    private var playbackPosition = 0L
    private var isPlayerReady = false
    private var isFullScreen = false
    private var playWhenReady = true
    private var currentItem = 0
    private var playbackState = Player.STATE_IDLE

    private var currentScaleMode = SCALE_MODE_FIT
    private var progressBarHandler: Handler? = null
    private var progressBarRunnable: Runnable? = null
    private var isLiveStream = false
    private val supportedFormats = mutableMapOf<String, String>()
    private var isTvMode = false
    private var controllerHideHandler: Handler? = null
    private var controllerHideRunnable: Runnable? = null
    private val TV_CONTROLLER_TIMEOUT = 5000L
    private val TV_SEEK_INCREMENT = 10000L

    // Picture-in-Picture
    private var isInPipMode = false
    private var pipReceiver: BroadcastReceiver? = null

    // Player listener reference for proper cleanup
    private var playerListener: Player.Listener? = null

    // Track selector for quality control
    private var trackSelector: DefaultTrackSelector? = null
    private var isDataSavingEnabled = false

    // Next-episode playback state.
    private var episodeList: ArrayList<EpisodeItem> = arrayListOf()
    private var currentEpisodeIndex: Int = -1
    private var providerUrl: String? = null
    private var scraperValue: String? = null
    private var isResolvingNextEpisode = false
    private var streamCandidates: ArrayList<StreamOption> = arrayListOf()
    private var currentStreamIndex = 0
    private var triedHeaderWorkaround = false
    private var requestHeaders: Map<String, String> = emptyMap()
    private var requestUserAgent: String? = null

    // Cascading format-retry state. Used when ExoPlayer cannot recognize the
    // container of an extension-less IPTV stream (ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED).
    // We re-prepare the stream forcing each known MIME type until one plays.
    private var formatRetryIndex = -1
    private val formatRetryMimeTypes: List<String?> by lazy {
        listOf(
            "application/x-mpegURL",        // HLS - most common for IPTV
            "video/mp2t",                   // raw MPEG-TS
            "application/dash+xml",         // DASH
            "application/vnd.ms-sstr+xml",  // SmoothStreaming
            "video/mp4",                    // progressive MP4
            null                            // auto-detect (last resort)
        )
    }

    companion object {
        private const val TAG = "PlayerActivity"
        private const val INCREMENT_MILLIS = 5000L
        private const val PROGRESS_BAR_UPDATE_INTERVAL_MS = 16
        private const val NEXT_EPISODE_OVERLAY_THRESHOLD_MS = 60_000L
        private const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        private const val SCALE_MODE_FIT = 0
        private const val SCALE_MODE_FILL = 1
        private const val SCALE_MODE_ZOOM = 2

        // PiP Actions
        private const val ACTION_PIP_PLAY = "com.samyak2403.iptvmine.PIP_PLAY"
        private const val ACTION_PIP_PAUSE = "com.samyak2403.iptvmine.PIP_PAUSE"
        private const val PIP_REQUEST_CODE = 101

        fun start(
            context: Context,
            name: String,
            streamUrl: String,
            headers: Map<String, String>? = null,
            watchHistoryEnabled: Boolean = false,
            movieLink: String? = null,
            movieImage: String? = null,
            providerUrl: String? = null,
            scraperValue: String? = null,
            episodes: ArrayList<EpisodeItem>? = null,
            episodeIndex: Int = -1,
            streamFallbacks: ArrayList<StreamOption>? = null
        ) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra("channel_name", name)
                putExtra("channel_stream_url", streamUrl)
                putExtra("watch_history_enabled", watchHistoryEnabled)
                putExtra("movie_link", movieLink)
                putExtra("movie_image", movieImage)
                putExtra("provider_url", providerUrl)
                putExtra("scraper_value", scraperValue)
                if (episodes != null && episodes.isNotEmpty()) {
                    putExtra("episodes", episodes)
                    putExtra("episode_index", episodeIndex)
                }
                if (streamFallbacks != null && streamFallbacks.isNotEmpty()) {
                    putExtra("stream_fallbacks", streamFallbacks)
                }
                if (headers != null) {
                    val bundle = Bundle()
                    for ((k, v) in headers) {
                        bundle.putString(k, v)
                    }
                    putExtra("channel_headers", bundle)
                }
            }
            context.startActivity(intent)
        }
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("file:/", ignoreCase = true) ||
            trimmed.startsWith("content:/", ignoreCase = true) ||
            trimmed.startsWith("/") -> trimmed
            !trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true) &&
            !trimmed.startsWith("rtmp://", ignoreCase = true) &&
            trimmed.isNotEmpty() -> "https://$trimmed"
            else -> trimmed
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        channelName = intent?.getStringExtra("channel_name")
        val rawUrl = intent?.getStringExtra("channel_stream_url")
        channelStreamUrl = rawUrl?.let { normalizeUrl(it) }

        // Next-episode playlist + alternate stream mirrors.
        providerUrl = intent?.getStringExtra("provider_url")
        scraperValue = intent?.getStringExtra("scraper_value")
        currentEpisodeIndex = intent?.getIntExtra("episode_index", -1) ?: -1
        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        val eps = intent?.getSerializableExtra("episodes") as? ArrayList<EpisodeItem>
        episodeList = eps ?: arrayListOf()
        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        val fallbacks = intent?.getSerializableExtra("stream_fallbacks") as? ArrayList<StreamOption>
        streamCandidates = fallbacks ?: arrayListOf()
        currentStreamIndex = 0

        // Detect TV mode
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        isTvMode = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        initializeSupportedFormats()
        initializeViews()

        if (channelStreamUrl.isNullOrBlank()) {
            Log.e(TAG, "No valid channel stream url received")
            showError("Invalid stream link: empty or blank")
            return
        }

        setupBackButton()
        setupFullScreenButton()
        setupPlayPauseButton()
        setupNavigationButtons()
        setupRepeatButton()

        isFullScreen = true
        // Don't change orientation on TV - it's always landscape
        if (!isTvMode) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        setupFullscreenWithNotch()
        updateFullscreenButtonIcon()

        channelName?.let {
            if (it.isNotEmpty()) {
                videoTitle.text = it
                videoTitle.isSelected = true
            }
        }

        // Setup TV-specific features
        if (isTvMode) {
            setupTvNavigation()
            setupTvControllerAutoHide()
        }

        // Setup PiP receiver
        setupPipReceiver()

        setupPlayer()
    }

    private fun initializeViews() {
        playerView = findViewById(R.id.playerView)
        youtubeOverlay = findViewById(R.id.youtube_overlay)
        progressBar = findViewById(R.id.progressBar)
        errorTextView = findViewById(R.id.errorTextView)
        nextEpisodeOverlay = findViewById(R.id.nextEpisodeOverlay)
        nextEpisodeOverlay.setOnClickListener { playNextEpisode() }

        // Volume and brightness control overlays
        brightnessControlPanel = findViewById(R.id.brightness_control_panel)
        brightnessProgressBar = findViewById(R.id.brightness_progressbar)
        brightnessText = findViewById(R.id.brightness_text)
        volumeControlPanel = findViewById(R.id.volume_control_panel)
        volumeProgressBar = findViewById(R.id.volume_progressbar)
        volumeText = findViewById(R.id.volume_text)

        setupSwipeGestures()

        backBtn = playerView.findViewById(R.id.backBtn)
        playPauseBtn = playerView.findViewById(R.id.playPauseBtn)
        fullScreenBtn = playerView.findViewById(R.id.fullScreenBtn)
        repeatBtn = playerView.findViewById(R.id.repeatBtn)
        prevBtn = playerView.findViewById(R.id.prevBtn)
        nextBtn = playerView.findViewById(R.id.nextBtn)
        nextEpisodeBtn = playerView.findViewById(R.id.nextEpisodeBtn)
        videoTitle = playerView.findViewById(R.id.videoTitle)
        exoLiveText = playerView.findViewById(R.id.exo_live_text)
        topController = playerView.findViewById(R.id.topController)
        bottomController = playerView.findViewById(R.id.bottomController)
        audioTrackBtn = playerView.findViewById(R.id.audioTrackBtn)
        subtitleBtn = playerView.findViewById(R.id.subtitleBtn)

        // Setup PiP button
        setupPipButton()

        // Setup audio track button
        setupAudioTrackButton()

        // Setup subtitle button
        setupSubtitleButton()

        // Hide unnecessary buttons on TV
        if (isTvMode) {
            fullScreenBtn.visibility = View.GONE
        }

        updateScaleMode()
    }

    private fun setupPipButton() {
        val pipBtn: ImageButton? = playerView.findViewById(R.id.pipBtn)
        pipBtn?.let { btn ->
            // Hide PiP button on TV (not supported)
            if (isTvMode) {
                btn.visibility = View.GONE
                return
            }

            // Check if PiP is supported
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                btn.visibility = View.VISIBLE
                btn.setOnClickListener {
                    enterPipMode()
                }
            } else {
                btn.visibility = View.GONE
            }
        }
    }

    private fun setupAudioTrackButton() {
        audioTrackBtn.setOnClickListener {
            showAudioTrackSelector()
        }
    }

    private fun updateAudioTrackButtonVisibility() {
        player?.let { exoPlayer ->
            val audioTrackCount = getAvailableAudioTracks().size
            audioTrackBtn.visibility = if (audioTrackCount > 1) View.VISIBLE else View.GONE
        } ?: run {
            audioTrackBtn.visibility = View.GONE
        }
    }

    private fun getAvailableAudioTracks(): List<Pair<Int, String>> {
        val audioTracks = mutableListOf<Pair<Int, String>>()
        player?.currentTracks?.groups?.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val language = format.language ?: "Unknown"
                    val label = format.label ?: ""
                    val channels = when (format.channelCount) {
                        1 -> "Mono"
                        2 -> "Stereo"
                        6 -> "5.1"
                        8 -> "7.1"
                        else -> "${format.channelCount}ch"
                    }
                    val trackName = buildString {
                        if (label.isNotEmpty()) {
                            append(label)
                        } else {
                            append(getLanguageName(language))
                        }
                        if (format.channelCount > 0) {
                            append(" ($channels)")
                        }
                    }
                    audioTracks.add(Pair(groupIndex, trackName))
                }
            }
        }
        return audioTracks
    }

    private fun getLanguageName(languageCode: String): String {
        return try {
            java.util.Locale(languageCode).displayLanguage
        } catch (e: Exception) {
            languageCode
        }
    }

    private fun showAudioTrackSelector() {
        val audioTracks = getAvailableAudioTracks()
        if (audioTracks.isEmpty()) {
            Toast.makeText(this, "No audio tracks available", Toast.LENGTH_SHORT).show()
            return
        }

        val trackNames = audioTracks.map { it.second }.toTypedArray()
        val currentTrackIndex = getCurrentAudioTrackIndex()

        android.app.AlertDialog.Builder(this)
            .setTitle("Select Audio Track")
            .setSingleChoiceItems(trackNames, currentTrackIndex) { dialog, which ->
                selectAudioTrack(which)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getCurrentAudioTrackIndex(): Int {
        var currentIndex = 0
        player?.currentTracks?.groups?.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    if (group.isTrackSelected(trackIndex)) {
                        return currentIndex
                    }
                    currentIndex++
                }
            }
        }
        return 0
    }

    private fun selectAudioTrack(trackIndex: Int) {
        player?.let { exoPlayer ->
            var currentIndex = 0
            exoPlayer.currentTracks.groups.forEachIndexed { groupIndex, group ->
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until group.length) {
                        if (currentIndex == trackIndex) {
                            trackSelector?.setParameters(
                                trackSelector!!.buildUponParameters()
                                    .setOverrideForType(
                                        TrackSelectionOverride(group.mediaTrackGroup, i)
                                    )
                            )
                            val trackName = getAvailableAudioTracks().getOrNull(trackIndex)?.second ?: "Track ${trackIndex + 1}"
                            Toast.makeText(this, "Audio: $trackName", Toast.LENGTH_SHORT).show()
                            return
                        }
                        currentIndex++
                    }
                }
            }
        }
    }

    private fun setupSubtitleButton() {
        subtitleBtn.setOnClickListener {
            showSubtitleTrackSelector()
        }
    }

    private fun updateSubtitleButtonVisibility() {
        player?.let { exoPlayer ->
            val subtitleTrackCount = getAvailableSubtitleTracks().size
            subtitleBtn.visibility = if (subtitleTrackCount > 0) View.VISIBLE else View.GONE
        } ?: run {
            subtitleBtn.visibility = View.GONE
        }
    }

    private fun getAvailableSubtitleTracks(): List<Pair<Int, String>> {
        val subtitleTracks = mutableListOf<Pair<Int, String>>()
        player?.currentTracks?.groups?.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val language = format.language ?: "Unknown"
                    val label = format.label ?: ""
                    val trackName = buildString {
                        if (label.isNotEmpty()) {
                            append(label)
                        } else {
                            append(getLanguageName(language))
                        }
                    }
                    subtitleTracks.add(Pair(groupIndex, trackName))
                }
            }
        }
        return subtitleTracks
    }

    private fun getCurrentSubtitleTrackIndex(): Int {
        val parameters = trackSelector?.parameters ?: return 0
        if (parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
            return 0 // "Off"
        }
        var currentIndex = 1 // 0 is "Off"
        player?.currentTracks?.groups?.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    if (group.isTrackSelected(trackIndex)) {
                        return currentIndex
                    }
                    currentIndex++
                }
            }
        }
        return 0 // Default to "Off" if none is selected
    }

    private fun showSubtitleTrackSelector() {
        val subtitleTracks = getAvailableSubtitleTracks()
        if (subtitleTracks.isEmpty()) {
            Toast.makeText(this, "No subtitles available", Toast.LENGTH_SHORT).show()
            return
        }

        val options = mutableListOf<String>()
        options.add("Off")
        options.addAll(subtitleTracks.map { it.second })

        val currentTrackIndex = getCurrentSubtitleTrackIndex()

        android.app.AlertDialog.Builder(this)
            .setTitle("Select Subtitles")
            .setSingleChoiceItems(options.toTypedArray(), currentTrackIndex) { dialog, which ->
                selectSubtitleTrack(which - 1)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun selectSubtitleTrack(trackIndex: Int) {
        player?.let { exoPlayer ->
            val builder = trackSelector?.buildUponParameters() ?: return
            if (trackIndex < 0) {
                // "Off" selected
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                trackSelector?.setParameters(builder)
                Toast.makeText(this, "Subtitles: Off", Toast.LENGTH_SHORT).show()
            } else {
                var currentIndex = 0
                exoPlayer.currentTracks.groups.forEachIndexed { groupIndex, group ->
                    if (group.type == C.TRACK_TYPE_TEXT) {
                        for (i in 0 until group.length) {
                            if (currentIndex == trackIndex) {
                                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(
                                        TrackSelectionOverride(group.mediaTrackGroup, i)
                                    )
                                trackSelector?.setParameters(builder)
                                val trackName = getAvailableSubtitleTracks().getOrNull(trackIndex)?.second ?: "Track ${trackIndex + 1}"
                                Toast.makeText(this, "Subtitles: $trackName", Toast.LENGTH_SHORT).show()
                                return
                            }
                            currentIndex++
                        }
                    }
                }
            }
        }
    }

    private fun setupPlayer() {
        try {
            // Check data saving mode preference
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            isDataSavingEnabled = prefs.getBoolean("data_saving_enabled", false)

            val headersBundle = intent?.getBundleExtra("channel_headers")
            val headersMap = mutableMapOf<String, String>()
            var userAgent: String? = null
            if (headersBundle != null) {
                for (key in headersBundle.keySet()) {
                    val value = headersBundle.getString(key)
                    if (value != null) {
                        if (key.equals("user-agent", ignoreCase = true)) {
                            userAgent = value
                        } else {
                            headersMap[key] = value
                        }
                    }
                }
            }

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true)

            if (userAgent != null) {
                httpDataSourceFactory.setUserAgent(userAgent)
            } else {
                httpDataSourceFactory.setUserAgent(CHROME_USER_AGENT)
            }

            if (headersMap.isNotEmpty()) {
                httpDataSourceFactory.setDefaultRequestProperties(headersMap)
            }

            // Persist for the background content probe and 403 header retry.
            requestHeaders = headersMap.toMap()
            requestUserAgent = userAgent

            // Setup track selector with data saving constraints
            trackSelector = DefaultTrackSelector(this).apply {
                setParameters(
                    buildUponParameters()
                        .setMaxVideoSizeSd() // Limit to SD quality when data saving is enabled
                        .setMaxVideoBitrate(if (isDataSavingEnabled) 800_000 else Int.MAX_VALUE) // 800kbps for data saving
                        .setForceLowestBitrate(isDataSavingEnabled)
                )
            }

            val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)

            // Route rtmp:// (and rtmps://) to the RTMP data source, everything else to the
            // default one, so HTTP, file, content and RTMP live streams all work.
            val rtmpAwareFactory = RtmpAwareDataSourceFactory(
                dataSourceFactory,
                androidx.media3.datasource.rtmp.RtmpDataSource.Factory()
            )

            // Maximise progressive-container support: constant-bitrate seeking for
            // MP3/ADTS/AMR, and full MPEG-TS audio stream detection (AC-3/E-AC-3/DTS).
            val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
                .setConstantBitrateSeekingAlwaysEnabled(true)
                .setTsExtractorFlags(
                    androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
                )

            // Buffer tuning for smoother playback on weak/variable connections.
            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30_000,   // min buffer
                    60_000,   // max buffer
                    2_500,    // buffer required before playback starts
                    5_000     // buffer required after a rebuffer
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            player = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector!!)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .setHandleAudioBecomingNoisy(true)
                .setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                .setLoadControl(loadControl)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(rtmpAwareFactory, extractorsFactory)
                )
                .build()

            playerView.player = player
            playerView.keepScreenOn = true

            // Style subtitles for better readability over video.
            try {
                playerView.subtitleView?.setStyle(
                    androidx.media3.ui.CaptionStyleCompat(
                        android.graphics.Color.WHITE,
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                        androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        android.graphics.Color.BLACK,
                        null
                    )
                )
                playerView.subtitleView?.setApplyEmbeddedStyles(false)
                playerView.subtitleView?.setApplyEmbeddedFontSizes(false)
            } catch (e: Exception) {
                Log.e(TAG, "Error styling subtitles", e)
            }
            playerView.controllerHideOnTouch = true
            playerView.controllerShowTimeoutMs = 3000
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)

            youtubeOverlay
                .player(player!!)
                .playerView(playerView)
                .performListener(object : YouTubeOverlay.PerformListener {
                    override fun onAnimationStart() {
                        youtubeOverlay.visibility = View.VISIBLE
                    }

                    override fun onAnimationEnd() {
                        youtubeOverlay.visibility = View.GONE
                    }
                })

            val mediaItem = detectAndCreateMediaItem(channelStreamUrl ?: "")

            // Create and store listener reference for proper cleanup
            playerListener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    this@PlayerActivity.playbackState = state
                    when (state) {
                        Player.STATE_READY -> {
                            this@PlayerActivity.isPlayerReady = true
                            // Stream is playing fine, clear any pending retry state
                            formatRetryIndex = -1
                            triedHeaderWorkaround = false
                            // Now that the timeline is known, resolve real live status
                            // so VOD content keeps its seek bar and only true live
                            // streams show the LIVE badge.
                            checkIfLiveStream()
                            progressBar.visibility = View.GONE
                            playerView.visibility = View.VISIBLE
                            startProgressBarUpdates()
                            updatePlayPauseButton(player?.isPlaying == true)
                            updateAudioTrackButtonVisibility()
                            updateSubtitleButtonVisibility()
                        }
                        Player.STATE_BUFFERING -> {
                            progressBar.visibility = View.VISIBLE
                        }
                        Player.STATE_ENDED -> {
                            updatePlayPauseButton(false)
                            maybeShowNextEpisodeOverlay()
                        }
                        Player.STATE_IDLE -> {
                            stopProgressBarUpdates()
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayPauseButton(isPlaying)
                }

                override fun onPlayerError(error: PlaybackException) {
                    handlePlayerError(error)
                }

                override fun onTracksChanged(tracks: Tracks) {
                    updateAudioTrackButtonVisibility()
                    updateSubtitleButtonVisibility()
                }

                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    // Timeline now reflects whether the stream is live (dynamic) or VOD.
                    checkIfLiveStream()
                }
            }

            val watchHistoryEnabled = intent?.getBooleanExtra("watch_history_enabled", false) ?: false
            val movieLink = intent?.getStringExtra("movie_link")
            var startPosition = 0L
            if (watchHistoryEnabled && !movieLink.isNullOrBlank()) {
                startPosition = WatchHistoryRepository.getProgress(this, movieLink)
            }

            player?.apply {
                addListener(playerListener!!)
                setPlayWhenReady(true)
            }

            // Probe the stream's real content type on a background thread before
            // preparing. Extension-less IPTV/scraper links often lack a usable
            // Content-Type, making ExoPlayer fall back to a progressive source and fail.
            // The probe forces the correct MediaSource (HLS/DASH/TS) and detects dead
            // links that return an HTML page instead of media.
            probeAndPrepare(channelStreamUrl ?: "", mediaItem, startPosition)

            setupProgressBarHandler()
            checkIfLiveStream()
            playbackState = player?.playbackState ?: Player.STATE_IDLE

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up player", e)
            showError("Error setting up player: ${e.message}")
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setupFullscreenWithNotch() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.apply {
                    hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                )
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
            }
            Log.d(TAG, "Fullscreen with notch support set up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up fullscreen with notch support", e)
        }
    }

    private fun updateScaleMode() {
        playerView.resizeMode = when (currentScaleMode) {
            SCALE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            SCALE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun setupProgressBarHandler() {
        try {
            progressBarHandler = Handler(Looper.getMainLooper())
            progressBarRunnable = object : Runnable {
                override fun run() {
                    if (player?.isPlaying == true && !isFinishing && !isDestroyed) {
                        updateProgressBar()
                        if (!isFinishing && !isDestroyed && progressBarHandler != null) {
                            progressBarHandler?.postDelayed(this, PROGRESS_BAR_UPDATE_INTERVAL_MS.toLong())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up progress bar handler", e)
        }
    }

    private fun updateProgressBar() {
        try {
            player?.let {
                if (isLiveStream) {
                    playerView.findViewById<View>(androidx.media3.ui.R.id.exo_progress)?.visibility = View.GONE
                    playerView.findViewById<View>(androidx.media3.ui.R.id.exo_position)?.visibility = View.GONE
                    playerView.findViewById<View>(androidx.media3.ui.R.id.exo_duration)?.visibility = View.GONE
                    updateLiveIndicator()
                } else {
                    playerView.findViewById<View>(androidx.media3.ui.R.id.exo_progress)?.visibility = View.VISIBLE
                    playerView.findViewById<View>(androidx.media3.ui.R.id.exo_position)?.visibility = View.VISIBLE
                    playerView.findViewById<View>(androidx.media3.ui.R.id.exo_duration)?.visibility = View.VISIBLE
                    exoLiveText.visibility = View.GONE
                }
            }
            maybeShowNextEpisodeOverlay()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating progress bar", e)
        }
    }

    private fun hasNextEpisode(): Boolean {
        return StreamResolverHolder.resolver != null &&
                currentEpisodeIndex in 0 until (episodeList.size - 1)
    }

    private fun updateNextEpisodeButtonVisibility() {
        if (::nextEpisodeBtn.isInitialized) {
            nextEpisodeBtn.visibility = if (hasNextEpisode()) View.VISIBLE else View.GONE
        }
    }

    /**
     * Disney+/Hotstar style: surface the "Next Episode" overlay near the end of a VOD
     * episode (or once ended), and whenever the controls are visible.
     */
    private fun maybeShowNextEpisodeOverlay() {
        if (!::nextEpisodeOverlay.isInitialized) return
        if (!hasNextEpisode()) {
            if (nextEpisodeOverlay.visibility != View.GONE) nextEpisodeOverlay.visibility = View.GONE
            return
        }
        val p = player
        if (p == null) {
            nextEpisodeOverlay.visibility = View.GONE
            return
        }
        val duration = p.duration
        val isVod = duration != C.TIME_UNSET && duration > 0 && !p.isCurrentMediaItemDynamic
        val remaining = if (isVod) duration - p.currentPosition else Long.MAX_VALUE
        val nearEnd = isVod && remaining in 1..NEXT_EPISODE_OVERLAY_THRESHOLD_MS
        val ended = p.playbackState == Player.STATE_ENDED
        val controllerVisible = try { playerView.isControllerFullyVisible } catch (e: Exception) { false }
        val show = !isResolvingNextEpisode && (nearEnd || ended || controllerVisible)
        nextEpisodeOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun playNextEpisode() {
        if (isResolvingNextEpisode) return
        if (!hasNextEpisode()) {
            Toast.makeText(this, "No next episode available", Toast.LENGTH_SHORT).show()
            return
        }
        val resolver = StreamResolverHolder.resolver ?: return
        val nextIndex = currentEpisodeIndex + 1
        val nextEpisode = episodeList.getOrNull(nextIndex) ?: return

        isResolvingNextEpisode = true
        nextEpisodeBtn.isEnabled = false
        nextEpisodeOverlay.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        errorTextView.visibility = View.GONE
        Toast.makeText(this, "Loading ${nextEpisode.title}...", Toast.LENGTH_SHORT).show()

        resolver.resolve(
            providerUrl ?: "",
            scraperValue ?: "",
            nextEpisode.link,
            nextEpisode.type
        ) { streams, error ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                isResolvingNextEpisode = false
                nextEpisodeBtn.isEnabled = true
                val first = streams?.firstOrNull()
                if (first == null || first.url.isBlank()) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, error ?: "Couldn't load the next episode", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                streamCandidates = ArrayList(streams)
                currentStreamIndex = 0
                loadEpisodeInPlace(nextIndex, nextEpisode.title, first.url, first.headers)
            }
        }
    }

    /**
     * Switches the current player to a newly resolved episode without recreating the
     * Activity. Saves the outgoing episode's position, swaps stream + headers, and
     * rebuilds playback through the existing setup path.
     */
    private fun loadEpisodeInPlace(index: Int, title: String, url: String, headers: Map<String, String>?) {
        releasePlayer()
        currentEpisodeIndex = index
        channelName = title
        channelStreamUrl = normalizeUrl(url)
        playbackPosition = 0L
        isPlayerReady = false
        formatRetryIndex = -1
        triedHeaderWorkaround = false

        intent.putExtra("channel_name", title)
        intent.putExtra("channel_stream_url", url)
        intent.putExtra("episode_index", index)
        val baseMovieLink = intent.getStringExtra("movie_link")?.substringBefore('#')
        if (!baseMovieLink.isNullOrBlank()) {
            intent.putExtra("movie_link", "$baseMovieLink#${Uri.encode(title)}")
        }
        if (headers != null && headers.isNotEmpty()) {
            val bundle = Bundle()
            for ((k, v) in headers) bundle.putString(k, v)
            intent.putExtra("channel_headers", bundle)
        } else {
            intent.removeExtra("channel_headers")
        }

        errorTextView.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        nextEpisodeOverlay.visibility = View.GONE
        if (::videoTitle.isInitialized) videoTitle.text = title

        setupPlayer()
        updateNextEpisodeButtonVisibility()
        Toast.makeText(this, "Playing $title", Toast.LENGTH_SHORT).show()
    }

    private fun updateLiveIndicator() {
        runOnUiThread {
            if (isLiveStream) {
                exoLiveText.visibility = View.VISIBLE
                player?.let { player ->
                    if (player.isCurrentMediaItemLive) {
                        val currentPosition = player.currentPosition
                        val duration = player.duration

                        if (duration > 0 && duration - currentPosition < 10000) {
                            exoLiveText.text = "LIVE"
                            exoLiveText.setBackgroundResource(R.drawable.live_indicator_background)
                        } else {
                            val behindLiveMs = duration - currentPosition
                            exoLiveText.text = formatTimeBehindLive(behindLiveMs)
                            exoLiveText.setBackgroundResource(R.drawable.live_indicator_background)
                            exoLiveText.setTextColor(
                                ContextCompat.getColor(this@PlayerActivity, android.R.color.holo_orange_light)
                            )
                        }
                    } else {
                        exoLiveText.text = "LIVE"
                        exoLiveText.setBackgroundResource(R.drawable.live_indicator_background)
                        exoLiveText.setTextColor(
                            ContextCompat.getColor(this, android.R.color.white)
                        )
                    }
                }
            } else {
                exoLiveText.visibility = View.GONE
            }
        }
    }

    private fun formatTimeBehindLive(millis: Long): String {
        return if (millis < 60000) {
            "${millis / 1000}s behind"
        } else {
            "${millis / 60000}m behind"
        }
    }

    private fun startProgressBarUpdates() {
        progressBarHandler?.let { handler ->
            progressBarRunnable?.let { runnable ->
                handler.removeCallbacks(runnable)
                handler.post(runnable)
            }
        }
    }

    private fun stopProgressBarUpdates() {
        try {
            progressBarHandler?.let { handler ->
                progressBarRunnable?.let { runnable ->
                    handler.removeCallbacks(runnable)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping progress bar updates", e)
        }
    }

    private fun playStream(url: String) {
        try {
            if (player == null) {
                setupPlayer()
                return
            }

            Log.d(TAG, "Attempting to play stream: $url")
            checkIfLiveStream()

            val mediaItem = detectAndCreateMediaItem(url)
            player?.apply {
                clearMediaItems()
                setMediaItem(mediaItem)
                prepare()
                play()
            }

            playPauseBtn.setImageResource(R.drawable.pause_icon)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing stream", e)
            showError("Error playing stream: ${e.message}")
        }
    }

    private fun checkIfLiveStream() {
        try {
            // Determine live status from ExoPlayer's actual timeline rather than guessing
            // from URL keywords. A dynamic (growing) window means a genuine live stream;
            // VOD content (e.g. an episode served over HLS) is not dynamic and must keep
            // its seek bar. Falls back to false until the timeline is known.
            isLiveStream = player?.isCurrentMediaItemDynamic ?: false

            if (isLiveStream) {
                exoLiveText.visibility = View.VISIBLE
            } else {
                exoLiveText.visibility = View.GONE
            }
            updateProgressBar()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if live stream", e)
            isLiveStream = false
        }
    }

    private fun setupFullScreenButton() {
        fullScreenBtn.setOnClickListener {
            currentScaleMode = (currentScaleMode + 1) % 3
            updateScaleMode()

            val scaleModeName = when (currentScaleMode) {
                SCALE_MODE_FILL -> "Fill"
                SCALE_MODE_ZOOM -> "Zoom"
                else -> "Fit"
            }
            Toast.makeText(this, "Scale Mode: $scaleModeName", Toast.LENGTH_SHORT).show()
        }

        fullScreenBtn.setOnLongClickListener {
            toggleFullscreen()
            true
        }
    }

    private fun toggleFullscreen() {
        isFullScreen = !isFullScreen
        try {
            if (isFullScreen) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                setupFullscreenWithNotch()
            } else {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(true)
                    window.insetsController?.show(
                        WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
            updateFullscreenButtonIcon()
            Log.d(TAG, "Toggled fullscreen mode: ${if (isFullScreen) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling fullscreen mode", e)
        }
    }

    private fun updateFullscreenButtonIcon() {
        fullScreenBtn.setImageResource(
            if (isFullScreen) R.drawable.fullscreen_exit_icon
            else R.drawable.fullscreen_icon
        )
    }

    private fun initializeSupportedFormats() {
        supportedFormats.apply {
            put("m3u8", "application/x-mpegURL")
            put("mpd", "application/dash+xml")
            put("ism", "application/vnd.ms-sstr+xml")
            put("ts", "video/mp2t")
            put("mp4", "video/mp4")
            put("webm", "video/webm")
            put("mkv", "video/x-matroska")
            put("avi", "video/x-msvideo")
            put("mov", "video/quicktime")
            put("flv", "video/x-flv")
            put("wmv", "video/x-ms-wmv")
            put("3gp", "video/3gpp")
        }
    }

    private enum class ProbeResult { HLS, DASH, TS, MP4, HTML, UNKNOWN }

    /**
     * Probes the stream URL on a background thread to determine its real content type,
     * then prepares the player with the correct MediaSource. Falls back to [fallbackItem]
     * when the type can't be determined. If the server returns an HTML page (dead/expired
     * link) it tries the next mirror or shows a clear error.
     */
    private fun probeAndPrepare(url: String, fallbackItem: MediaItem, startPosition: Long) {
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)) {
            prepareWithItem(fallbackItem, startPosition)
            return
        }
        Thread {
            val result = try {
                probeContent(url)
            } catch (e: Exception) {
                Log.w(TAG, "Content probe failed: ${e.message}")
                ProbeResult.UNKNOWN
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                Log.d(TAG, "Content probe result: $result")
                if (result == ProbeResult.HTML) {
                    if (tryNextStreamCandidate()) return@runOnUiThread
                    showError("This link isn't a playable video. The server returned a web page, so the stream may be expired, geo-blocked, or require sign-in.")
                    return@runOnUiThread
                }
                val mime = when (result) {
                    ProbeResult.HLS -> "application/x-mpegURL"
                    ProbeResult.DASH -> "application/dash+xml"
                    ProbeResult.TS -> "video/mp2t"
                    ProbeResult.MP4 -> "video/mp4"
                    else -> null
                }
                val item = if (mime != null) {
                    MediaItem.Builder().setUri(Uri.parse(url)).setMimeType(mime).build()
                } else {
                    fallbackItem
                }
                prepareWithItem(item, startPosition)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun prepareWithItem(item: MediaItem, startPosition: Long) {
        player?.apply {
            if (startPosition > 0L) setMediaItem(item, startPosition) else setMediaItem(item)
            setPlayWhenReady(true)
            prepare()
        }
    }

    private fun probeContent(url: String): ProbeResult {
        var connection: HttpURLConnection? = null
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            connection = conn
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("Range", "bytes=0-8191")
            conn.setRequestProperty("User-Agent", requestUserAgent ?: CHROME_USER_AGENT)
            for ((k, v) in requestHeaders) {
                try { conn.setRequestProperty(k, v) } catch (_: Exception) {}
            }
            conn.connect()

            val status = conn.responseCode
            val contentType = (conn.contentType ?: "").lowercase()
            val buf = ByteArray(1024)
            val read = try {
                val input = if (status in 200..299) conn.inputStream else conn.errorStream
                input?.use { readUpTo(it, buf) } ?: 0
            } catch (e: Exception) { 0 }
            val head = if (read > 0) String(buf, 0, read, Charsets.ISO_8859_1) else ""
            val headTrim = head.trimStart()
            val headLower = headTrim.lowercase()

            when {
                contentType.contains("mpegurl") || contentType.contains("vnd.apple.mpegurl") -> return ProbeResult.HLS
                contentType.contains("dash+xml") -> return ProbeResult.DASH
                contentType.contains("mp2t") -> return ProbeResult.TS
                contentType.startsWith("video/mp4") || contentType.startsWith("audio/mp4") -> return ProbeResult.MP4
                (contentType.startsWith("text/html") || contentType.contains("xhtml")) &&
                    (headLower.startsWith("<!doctype html") || headLower.startsWith("<html") ||
                     headLower.contains("<head") || headLower.contains("<body")) -> return ProbeResult.HTML
            }

            if (read > 0) {
                if (headTrim.startsWith("#EXTM3U")) return ProbeResult.HLS
                if (headLower.startsWith("<mpd") || headLower.contains("urn:mpeg:dash:schema")) return ProbeResult.DASH
                if (headLower.startsWith("<!doctype html") || headLower.startsWith("<html")) return ProbeResult.HTML
                if (buf[0] == 0x47.toByte() && read > 188 && buf[188] == 0x47.toByte()) return ProbeResult.TS
                if (read > 8 && buf[4] == 'f'.code.toByte() && buf[5] == 't'.code.toByte() &&
                    buf[6] == 'y'.code.toByte() && buf[7] == 'p'.code.toByte()) return ProbeResult.MP4
            }

            if (status >= 400 &&
                (headLower.startsWith("<!doctype html") || headLower.startsWith("<html") || contentType.startsWith("text/"))) {
                return ProbeResult.HTML
            }
            return ProbeResult.UNKNOWN
        } finally {
            connection?.disconnect()
        }
    }

    private fun readUpTo(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val r = input.read(buf, total, buf.size - total)
            if (r == -1) break
            total += r
        }
        return total
    }

    private fun detectAndCreateMediaItem(url: String): MediaItem {
        if (!url.startsWith("http") && !url.startsWith("rtmp")) {
            Log.w(TAG, "Invalid URL scheme: $url")
        }

        val extension = url.substringAfterLast('.', "").lowercase()
        val containsFormat = getFormatIdentifierFromUrl(url)

        val builder = MediaItem.Builder().setUri(Uri.parse(url))

        val detectedFormat = when {
            containsFormat.isNotEmpty() && supportedFormats.containsKey(containsFormat) -> {
                Log.d(TAG, "Format detected from URL: $containsFormat")
                supportedFormats[containsFormat]
            }
            extension.isNotEmpty() && supportedFormats.containsKey(extension) -> {
                Log.d(TAG, "Format detected from extension: $extension")
                supportedFormats[extension]
            }
            url.contains("/hls/") || url.contains("playlist") || url.contains("manifest") -> {
                Log.d(TAG, "HLS format inferred from URL pattern")
                supportedFormats["m3u8"]
            }
            url.contains("/dash/") -> {
                Log.d(TAG, "DASH format inferred from URL pattern")
                supportedFormats["mpd"]
            }
            else -> {
                Log.d(TAG, "No specific format detected, using auto-detection")
                null
            }
        }

        detectedFormat?.let {
            builder.setMimeType(it)
            Log.d(TAG, "Setting MIME type: $it")
        }

        // Do NOT infer live status from URL keywords. ExoPlayer auto-detects live
        // streams from the HLS/DASH manifest and applies the correct windowing.
        // Forcing live configuration onto VOD content breaks seeking and hides the
        // seek bar. Actual live status is resolved later via isCurrentMediaItemDynamic.

        return builder.build()
    }

    private fun getFormatIdentifierFromUrl(url: String): String {
        val formatIdentifiers = listOf(
            "format=m3u8", "format=mpd", "format=hls", "format=dash", "format=mp4",
            "type=m3u8", "type=mpd", "type=hls", "type=dash", "type=mp4",
            "playlist_type=m3u8", "stream_type=hls",
            "/hls/", "/dash/", "/m3u8/", "/mpd/"
        )

        for (identifier in formatIdentifiers) {
            if (url.lowercase().contains(identifier.lowercase())) {
                return if (identifier.contains("=")) {
                    identifier.substringAfter('=')
                } else {
                    identifier.replace("/", "")
                }
            }
        }
        return ""
    }

    private fun tryHttpsUrl(httpUrl: String): String {
        return if (httpUrl.startsWith("http://")) {
            "https://" + httpUrl.substring(7)
        } else {
            httpUrl
        }
    }

    private fun createAlternativeMediaItem(url: String): MediaItem? {
        if (!url.startsWith("http://")) {
            return null
        }
        val httpsUrl = tryHttpsUrl(url)
        return detectAndCreateMediaItem(httpsUrl)
    }

    private fun handlePlayerError(error: PlaybackException) {
        Log.e(TAG, "Playback error: ${error.errorCodeName}", error)

        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            Log.d(TAG, "Handling ERROR_CODE_BEHIND_LIVE_WINDOW - seeking to live edge")
            player?.apply {
                seekToDefaultPosition()
                prepare()
            }
            Toast.makeText(this, "Reconnecting to live stream...", Toast.LENGTH_SHORT).show()
            return
        }

        val errorCause = error.cause?.message ?: error.message ?: "Unknown error"
        Log.e(TAG, "Error details: $errorCause")

        var cleartextError = false
        var hlsParsingError = false
        var formatError = false
        var sourceError = false

        try {
            cleartextError = error.errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ||
                error.cause?.cause?.message?.contains("Cleartext HTTP traffic") == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for cleartext error", e)
        }

        try {
            hlsParsingError = error.cause?.message?.let {
                it.contains("Input does not start with the #EXTM3U header") ||
                it.contains("contentIsMalformed=true")
            } == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for HLS parsing error", e)
        }

        try {
            formatError = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
                error.cause?.message?.let { it.contains("Format") || it.contains("No decoder") } == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for format error", e)
        }

        try {
            sourceError = error.errorCodeName == "ERROR_CODE_IO_UNSPECIFIED" ||
                    error.errorCodeName == "ERROR_CODE_IO_BAD_HTTP_STATUS"
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for source error", e)
        }

        // Detect the "unrecognized container" case that is common for extension-less
        // IPTV streams. ExoPlayer defaults to a progressive (extractor) source and none
        // of the extractors can read an HLS/TS/DASH stream, producing this error.
        var containerUnsupported = false
        try {
            containerUnsupported =
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ||
                error.cause?.message?.contains("UnrecognizedInputFormatException") == true ||
                error.cause?.message?.contains("None of the available extractors") == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for container unsupported error", e)
        }

        // HTTP 403 Forbidden: the host is rejecting the request, usually because it
        // wants a Referer/Origin or a browser User-Agent. Retry once with those headers
        // before falling back to other mirrors. Cascading MIME types can't fix this.
        var forbiddenError = false
        try {
            val cause = error.cause
            val httpCode = if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                cause.responseCode
            } else -1
            forbiddenError = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS &&
                (httpCode == 403 || httpCode == 401 ||
                 (httpCode == -1 && (error.cause?.message?.contains("403") == true ||
                                     error.cause?.message?.contains("401") == true)))
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for 403", e)
        }

        when {
            cleartextError && channelStreamUrl?.startsWith("http://") == true -> {
                Log.d(TAG, "Attempting to use HTTPS instead of HTTP")
                val httpsUrl = tryHttpsUrl(channelStreamUrl ?: "")
                playStream(httpsUrl)
                return
            }
            forbiddenError -> {
                if (!triedHeaderWorkaround) {
                    Log.d(TAG, "403 Forbidden - retrying with Referer/Origin/browser headers")
                    triedHeaderWorkaround = true
                    retryWithEnhancedHeaders()
                    return
                }
                if (tryNextStreamCandidate()) return
                // Fall through to show the error below.
            }
            hlsParsingError -> {
                if (channelStreamUrl?.lowercase()?.endsWith(".m3u8") == true) {
                    Log.d(TAG, "HLS parsing failed, trying direct media without format hint")
                    val genericMediaItem = MediaItem.Builder()
                        .setUri(Uri.parse(channelStreamUrl))
                        .build()
                    player?.apply {
                        clearMediaItems()
                        setMediaItem(genericMediaItem)
                        prepare()
                        play()
                    }
                    return
                } else {
                    Log.d(TAG, "HLS parsing failed, trying raw URI")
                    val nonHlsMediaItem = MediaItem.fromUri(Uri.parse(channelStreamUrl ?: ""))
                    player?.apply {
                        clearMediaItems()
                        setMediaItem(nonHlsMediaItem)
                        prepare()
                        play()
                    }
                    return
                }
            }
            formatError || sourceError -> {
                Log.d(TAG, "Format/source error, trying alternative format")
                tryAlternativeFormat(channelStreamUrl ?: "")
                return
            }
            containerUnsupported -> {
                Log.d(TAG, "Container unsupported, cascading through known stream formats")
                tryNextFormat()
                return
            }
        }

        errorTextView.visibility = View.VISIBLE
        playerView.visibility = View.GONE

        errorTextView.text = when {
            forbiddenError -> "This source refused the connection (403). It may be expired or region-locked. Try another quality or provider."
            hlsParsingError -> "Invalid stream format. This stream may not be available."
            cleartextError -> "Insecure connection not allowed. Try using an HTTPS stream or check app settings."
            formatError -> "This media format is not supported on your device."
            error.errorCodeName == "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED" ||
            error.errorCodeName == "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT" ->
                "Network error. Please check your connection."
            error.errorCodeName == "ERROR_CODE_AUDIO_TRACK_INIT_FAILED" ->
                "Audio format not supported by your device."
            else -> "Playback error: ${error.errorCodeName}"
        }
    }

    /**
     * Cascades through known streaming container MIME types one at a time.
     * Each attempt re-prepares the player forcing a specific MIME type, which selects
     * the correct MediaSource (HLS/DASH/SS/Progressive). If that attempt also fails,
     * onPlayerError routes back here to try the next format. When all formats are
     * exhausted a final error is shown.
     */
    private fun tryNextFormat() {
        val streamUrl = channelStreamUrl
        if (streamUrl.isNullOrBlank()) {
            showFormatExhaustedError()
            return
        }

        formatRetryIndex++

        if (formatRetryIndex >= formatRetryMimeTypes.size) {
            Log.e(TAG, "Exhausted all format options for stream")
            showFormatExhaustedError()
            return
        }

        val mimeType = formatRetryMimeTypes[formatRetryIndex]
        Log.d(TAG, "Format retry ${formatRetryIndex + 1}/${formatRetryMimeTypes.size}: ${mimeType ?: "auto-detect"}")

        try {
            val builder = MediaItem.Builder().setUri(Uri.parse(streamUrl))
            mimeType?.let { builder.setMimeType(it) }

            errorTextView.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            progressBar.visibility = View.VISIBLE

            player?.apply {
                clearMediaItems()
                setMediaItem(builder.build())
                prepare()
                playWhenReady = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing format ${mimeType ?: "auto-detect"}: ${e.message}", e)
            // Advance to the next format immediately if this one threw synchronously
            tryNextFormat()
        }
    }

    private fun showFormatExhaustedError() {
        formatRetryIndex = -1
        // Before giving up, try the next mirror/quality for this item, if any.
        if (tryNextStreamCandidate()) return
        errorTextView.visibility = View.VISIBLE
        playerView.visibility = View.GONE
        progressBar.visibility = View.GONE
        errorTextView.text = "Unable to play this stream. The source may be expired or unsupported. Try another quality or provider."
    }

    /** Falls back to the next stream mirror/quality for the current item. */
    private fun tryNextStreamCandidate(): Boolean {
        if (currentStreamIndex + 1 >= streamCandidates.size) return false
        currentStreamIndex++
        triedHeaderWorkaround = false // give the new mirror its own header retry
        val next = streamCandidates[currentStreamIndex]
        Log.d(TAG, "Falling back to stream ${currentStreamIndex + 1}/${streamCandidates.size}")
        Toast.makeText(this, "Trying another source...", Toast.LENGTH_SHORT).show()
        applyStreamAndReload(next.url, next.headers)
        return true
    }

    /**
     * Retries the current stream after a 403 by adding a Referer/Origin (derived from the
     * stream host) and a browser User-Agent, which many CDNs require.
     */
    private fun retryWithEnhancedHeaders() {
        val url = channelStreamUrl
        if (url.isNullOrBlank()) {
            showError("Stream blocked by the server (403).")
            return
        }
        val enhanced = HashMap<String, String>()
        intent.getBundleExtra("channel_headers")?.let { b ->
            for (k in b.keySet()) b.getString(k)?.let { enhanced[k] = it }
        }
        try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme ?: "https"
            val host = uri.host
            if (host != null) {
                val origin = "$scheme://$host"
                if (enhanced.keys.none { it.equals("Referer", ignoreCase = true) }) {
                    enhanced["Referer"] = "$origin/"
                }
                if (enhanced.keys.none { it.equals("Origin", ignoreCase = true) }) {
                    enhanced["Origin"] = origin
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error building enhanced headers", e)
        }
        if (enhanced.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            enhanced["User-Agent"] = CHROME_USER_AGENT
        }
        applyStreamAndReload(url, enhanced)
    }

    /** Swaps the player to a different stream URL (mirror/quality) without recreating it. */
    private fun applyStreamAndReload(url: String, headers: Map<String, String>?) {
        releasePlayer()
        channelStreamUrl = normalizeUrl(url)
        formatRetryIndex = -1
        playbackPosition = 0L
        isPlayerReady = false
        intent.putExtra("channel_stream_url", url)
        if (headers != null && headers.isNotEmpty()) {
            val bundle = Bundle()
            for ((k, v) in headers) bundle.putString(k, v)
            intent.putExtra("channel_headers", bundle)
        } else {
            intent.removeExtra("channel_headers")
        }
        errorTextView.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        setupPlayer()
    }

    private fun tryAlternativeFormat(streamUrl: String) {
        // Delegate to the cascading format retry which handles failures sequentially.
        tryNextFormat()
    }

    private fun setupBackButton() {
        backBtn.setOnClickListener {
            Log.d(TAG, "Back button clicked")
            finish()
        }
    }

    private fun setupPlayPauseButton() {
        playPauseBtn.setOnClickListener {
            player?.let {
                if (it.isPlaying) {
                    it.pause()
                    playPauseBtn.setImageResource(R.drawable.play_icon)
                } else {
                    it.play()
                    playPauseBtn.setImageResource(R.drawable.pause_icon)
                }
            }
        }
        // Long-press to change playback speed.
        playPauseBtn.setOnLongClickListener {
            showPlaybackSpeedDialog()
            true
        }
    }

    private fun showPlaybackSpeedDialog() {
        val p = player ?: return
        val speeds = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val labels = speeds.map { if (it == 1.0f) "Normal (1x)" else "${it}x" }.toTypedArray()
        val current = p.playbackParameters.speed
        val checked = speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }.let { if (it < 0) 3 else it }
        android.app.AlertDialog.Builder(this)
            .setTitle("Playback Speed")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                player?.setPlaybackSpeed(speeds[which])
                Toast.makeText(this, "Speed: ${labels[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupNavigationButtons() {
        prevBtn.setOnClickListener {
            player?.let {
                val newPosition = maxOf(0, it.currentPosition - 10000)
                it.seekTo(newPosition)
            }
        }

        nextBtn.setOnClickListener {
            player?.let {
                val newPosition = minOf(it.duration, it.currentPosition + 10000)
                it.seekTo(newPosition)
            }
        }

        nextEpisodeBtn.setOnClickListener { playNextEpisode() }
        updateNextEpisodeButtonVisibility()
    }

    private fun setupRepeatButton() {
        repeatBtn.setOnClickListener {
            player?.let {
                when (it.repeatMode) {
                    Player.REPEAT_MODE_OFF -> {
                        it.repeatMode = Player.REPEAT_MODE_ONE
                        repeatBtn.setImageResource(R.drawable.repeat_one_icon)
                        Toast.makeText(this, "Repeat One", Toast.LENGTH_SHORT).show()
                    }
                    Player.REPEAT_MODE_ONE -> {
                        it.repeatMode = Player.REPEAT_MODE_ALL
                        repeatBtn.setImageResource(R.drawable.repeat_all_icon)
                        Toast.makeText(this, "Repeat All", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        it.repeatMode = Player.REPEAT_MODE_OFF
                        repeatBtn.setImageResource(R.drawable.repeat_off_icon)
                        Toast.makeText(this, "Repeat Off", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * Setup TV-specific D-pad navigation
     */
    private fun setupTvNavigation() {
        // Setup focus animations for all buttons
        // Animation setup removed as TvUtils is not present

        // Set initial focus to play/pause button
        playPauseBtn.requestFocus()

        Log.d(TAG, "TV navigation setup complete")
    }

    /**
     * Setup auto-hide for TV controller
     */
    private fun setupTvControllerAutoHide() {
        controllerHideHandler = Handler(Looper.getMainLooper())
        controllerHideRunnable = Runnable {
            if (!isFinishing && !isDestroyed) {
                playerView.hideController()
            }
        }
    }

    /**
     * Reset the controller auto-hide timer
     */
    private fun resetControllerHideTimer() {
        controllerHideHandler?.removeCallbacks(controllerHideRunnable!!)
        controllerHideHandler?.postDelayed(controllerHideRunnable!!, TV_CONTROLLER_TIMEOUT)
    }

    /**
     * Handle D-pad and media key events for TV
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!isTvMode) {
            return super.onKeyDown(keyCode, event)
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }

        // Show controller on any key press
        if (!playerView.isControllerFullyVisible) {
            playerView.showController()
            resetControllerHideTimer()
        } else {
            resetControllerHideTimer()
        }

        when (keyCode) {
            // Media control keys
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                player?.play()
                updatePlayPauseButton(true)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player?.pause()
                updatePlayPauseButton(false)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                seekForward()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                seekBackward()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                player?.stop()
                return true
            }

            // D-pad navigation when controller is hidden
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!playerView.isControllerFullyVisible) {
                    togglePlayPause()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!playerView.isControllerFullyVisible) {
                    seekBackward()
                    showSeekFeedback(-TV_SEEK_INCREMENT)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!playerView.isControllerFullyVisible) {
                    seekForward()
                    showSeekFeedback(TV_SEEK_INCREMENT)
                    return true
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    /**
     * Toggle play/pause state
     */
    private fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
                playPauseBtn.setImageResource(R.drawable.play_icon)
            } else {
                it.play()
                playPauseBtn.setImageResource(R.drawable.pause_icon)
            }
        }
    }

    /**
     * Seek forward by TV_SEEK_INCREMENT
     */
    private fun seekForward() {
        player?.let {
            val newPosition = minOf(it.duration, it.currentPosition + TV_SEEK_INCREMENT)
            it.seekTo(newPosition)
        }
    }

    /**
     * Seek backward by TV_SEEK_INCREMENT
     */
    private fun seekBackward() {
        player?.let {
            val newPosition = maxOf(0, it.currentPosition - TV_SEEK_INCREMENT)
            it.seekTo(newPosition)
        }
    }

    /**
     * Show visual feedback for seek operations on TV
     */
    private fun showSeekFeedback(seekAmount: Long) {
        val direction = if (seekAmount > 0) "+" else ""
        val seconds = seekAmount / 1000
        Toast.makeText(this, "${direction}${seconds}s", Toast.LENGTH_SHORT).show()
    }

    /**
     * Enter Picture-in-Picture mode
     */
    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                val params = buildPipParams()
                enterPictureInPictureMode(params)
            } else {
                Toast.makeText(this, "PiP not supported", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "PiP requires Android 8.0+", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(): PictureInPictureParams {
        val aspectRatio = Rational(16, 9)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)

        // Add play/pause action
        val actions = ArrayList<RemoteAction>()
        val isPlaying = player?.isPlaying == true

        val actionIntent = Intent(if (isPlaying) ACTION_PIP_PAUSE else ACTION_PIP_PLAY)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            PIP_REQUEST_CODE,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val icon = Icon.createWithResource(
            this,
            if (isPlaying) R.drawable.pause_icon else R.drawable.play_icon
        )
        val title = if (isPlaying) "Pause" else "Play"

        actions.add(RemoteAction(icon, title, title, pendingIntent))
        builder.setActions(actions)

        return builder.build()
    }

    private fun setupPipReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pipReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        ACTION_PIP_PLAY -> {
                            player?.play()
                            updatePipActions()
                        }
                        ACTION_PIP_PAUSE -> {
                            player?.pause()
                            updatePipActions()
                        }
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(ACTION_PIP_PLAY)
                addAction(ACTION_PIP_PAUSE)
            }
            // ContextCompat applies RECEIVER_NOT_EXPORTED only on API 33+ and registers
            // correctly on Android 12 (where that flag isn't a valid registerReceiver arg).
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                pipReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updatePipActions() {
        if (isInPipMode) {
            setPictureInPictureParams(buildPipParams())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode

        if (isInPictureInPictureMode) {
            // Hide controls in PiP
            playerView.useController = false
            topController.visibility = View.GONE
            bottomController.visibility = View.GONE
        } else {
            // Show controls when exiting PiP
            playerView.useController = true
            topController.visibility = View.VISIBLE
            bottomController.visibility = View.VISIBLE
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Auto-enter PiP when user presses home (if playing)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player?.isPlaying == true && !isTvMode) {
            enterPipMode()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        try {
            super.onConfigurationChanged(newConfig)
            when (newConfig.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> hideSystemUi()
                Configuration.ORIENTATION_PORTRAIT -> showSystemUi()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling configuration change", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        try {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
            supportActionBar?.hide()
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding system UI", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun showSystemUi() {
        try {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            supportActionBar?.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing system UI", e)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        try {
            super.onSaveInstanceState(outState)
            channelName?.let {
                outState.putString("channel_name", it)
            }
            channelStreamUrl?.let {
                outState.putString("channel_stream_url", it)
            }
            updateStartPosition()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving instance state", e)
        }
    }

    private fun updateStartPosition() {
        try {
            player?.let {
                playbackPosition = it.currentPosition
                currentItem = it.currentMediaItemIndex
                playWhenReady = it.playWhenReady

                if (intent?.getBooleanExtra("watch_history_enabled", false) == true) {
                    val title = intent?.getStringExtra("channel_name") ?: ""
                    val mLink = intent?.getStringExtra("movie_link") ?: ""
                    val mImage = intent?.getStringExtra("movie_image") ?: ""
                    val pUrl = intent?.getStringExtra("provider_url") ?: ""
                    val sVal = intent?.getStringExtra("scraper_value") ?: ""

                    if (mLink.isNotEmpty() && it.duration > 0) {
                        WatchHistoryRepository.saveProgress(
                            context = this,
                            title = title,
                            link = mLink,
                            streamUrl = channelStreamUrl ?: "",
                            imageUrl = mImage,
                            providerUrl = pUrl,
                            scraperValue = sVal,
                            position = playbackPosition,
                            duration = it.duration
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating start position", e)
        }
    }

    override fun onStart() {
        super.onStart()
        if (Util.SDK_INT > 23) {
            initializePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Util.SDK_INT <= 23 || !isPlayerReady) {
            initializePlayer()
        }
    }

    private fun initializePlayer() {
        if (player == null) {
            setupPlayer()
        } else {
            playerView.player = player
            val mediaItem = detectAndCreateMediaItem(channelStreamUrl ?: "")
            val mediaItems = listOf(mediaItem)
            player?.apply {
                setMediaItems(mediaItems, currentItem, playbackPosition)
                playWhenReady = this@PlayerActivity.playWhenReady
                prepare()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Don't release player if entering PiP mode
        if (Util.SDK_INT <= 23 && !isInPipMode) {
            updateStartPosition()
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        // Don't release player if in PiP mode
        if (Util.SDK_INT > 23 && !isInPipMode) {
            updateStartPosition()
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        try {
            // Don't release if in PiP mode
            if (isInPipMode) {
                Log.d(TAG, "Skipping player release - in PiP mode")
                return
            }

            stopProgressBarUpdates()
            player?.let { exoPlayer ->
                try {
                    // Remove all listeners first
                    playerListener?.let { listener ->
                        exoPlayer.removeListener(listener)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing listener", e)
                }

                try {
                    updateStartPosition()
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating start position during release", e)
                }

                try {
                    exoPlayer.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping player", e)
                }

                try {
                    exoPlayer.clearMediaItems()
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing media items", e)
                }

                try {
                    exoPlayer.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing player", e)
                }
                player = null
                isPlayerReady = false
            }
            playerView.player = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing player", e)
        }
    }

    override fun onDestroy() {
        try {
            stopProgressBarUpdates()
            releasePlayer()
            hidePanelsHandler.removeCallbacksAndMessages(null)
            progressBarHandler?.removeCallbacksAndMessages(null)
            progressBarHandler = null
            progressBarRunnable = null

            // Clean up TV controller handler
            controllerHideHandler?.removeCallbacksAndMessages(null)
            controllerHideHandler = null
            controllerHideRunnable = null

            // Unregister PiP receiver
            pipReceiver?.let {
                try {
                    unregisterReceiver(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error unregistering PiP receiver", e)
                }
            }
            pipReceiver = null

            super.onDestroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
            super.onDestroy()
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        if (isFinishing || isDestroyed) return
        try {
            playPauseBtn.setImageResource(
                if (isPlaying) R.drawable.pause_icon else R.drawable.play_icon
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating play/pause button", e)
        }
    }

    private fun showError(message: String) {
        errorTextView.visibility = View.VISIBLE
        errorTextView.text = message
        playerView.visibility = View.GONE
        progressBar.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeGestures() {
        swipeGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                volumeAccumulator = 0f
                isScrolling = false
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                isScrolling = true
                val startEvent = e1 ?: return false
                val viewWidth = playerView.width

                // Disney+ Hotstar UI: Left 50% = Brightness, Right 50% = Volume
                if (startEvent.x < viewWidth * 0.5f) {
                    adjustBrightness(distanceY)
                } else {
                    adjustVolume(distanceY)
                }
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            swipeGestureDetector.onTouchEvent(event)

            val action = event.action
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                val wasScrolling = isScrolling
                if (isScrolling) {
                    hidePanelsWithDelay()
                }
                isScrolling = false
                wasScrolling
            } else {
                isScrolling
            }
        }
    }

    private fun adjustBrightness(distanceY: Float) {
        val layoutParams = window.attributes
        var currentBrightness = layoutParams.screenBrightness

        if (currentBrightness < 0) {
            currentBrightness = try {
                android.provider.Settings.System.getInt(
                    contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS
                ) / 255f
            } catch (e: Exception) {
                0.5f
            }
        }

        val delta = distanceY / playerView.height
        var newBrightness = currentBrightness + delta
        newBrightness = newBrightness.coerceIn(0.01f, 1.0f)

        layoutParams.screenBrightness = newBrightness
        window.attributes = layoutParams

        showBrightnessPanel((newBrightness * 100).toInt())
    }

    private fun adjustVolume(distanceY: Float) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val delta = (distanceY / playerView.height) * maxVolume
        volumeAccumulator += delta

        val volumeChange = volumeChangeForScroll(volumeAccumulator)
        if (volumeChange != 0) {
            var newVolume = currentVolume + volumeChange
            newVolume = newVolume.coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
            volumeAccumulator -= volumeChange
            showVolumePanel(newVolume, maxVolume)
        } else {
            showVolumePanel(currentVolume, maxVolume)
        }
    }

    private fun volumeChangeForScroll(accumulator: Float): Int {
        return if (accumulator >= 1.0f) {
            accumulator.toInt()
        } else if (accumulator <= -1.0f) {
            accumulator.toInt()
        } else {
            0
        }
    }

    private fun showBrightnessPanel(percentage: Int) {
        hidePanelsHandler.removeCallbacks(hidePanelsRunnable)

        volumeControlPanel.visibility = View.GONE
        brightnessControlPanel.visibility = View.VISIBLE

        brightnessProgressBar.progress = percentage
        brightnessText.text = "$percentage%"
    }

    private fun showVolumePanel(volume: Int, maxVolume: Int) {
        hidePanelsHandler.removeCallbacks(hidePanelsRunnable)

        brightnessControlPanel.visibility = View.GONE
        volumeControlPanel.visibility = View.VISIBLE

        val percentage = ((volume.toFloat() / maxVolume) * 100).toInt()
        volumeProgressBar.progress = percentage
        volumeText.text = "$percentage%"
    }

    private fun hidePanelsWithDelay() {
        hidePanelsHandler.removeCallbacks(hidePanelsRunnable)
        hidePanelsHandler.postDelayed(hidePanelsRunnable, 1000L)
    }
}
