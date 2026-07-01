package com.samyak.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * A [DataSource.Factory] that routes `rtmp://`/`rtmps://` URIs to an RTMP data source and
 * everything else (http/https/file/content/asset…) to the default data source, so the
 * player handles the full range of IPTV/scraper stream URLs without the caller knowing
 * the scheme up front.
 */
@UnstableApi
class RtmpAwareDataSourceFactory(
    private val defaultFactory: DataSource.Factory,
    private val rtmpFactory: DataSource.Factory
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        RtmpAwareDataSource(defaultFactory.createDataSource(), rtmpFactory.createDataSource())
}

@UnstableApi
private class RtmpAwareDataSource(
    private val defaultSource: DataSource,
    private val rtmpSource: DataSource
) : DataSource {

    private var active: DataSource = defaultSource

    override fun addTransferListener(transferListener: TransferListener) {
        defaultSource.addTransferListener(transferListener)
        rtmpSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        active = if (dataSpec.uri.scheme.equals("rtmp", ignoreCase = true) ||
            dataSpec.uri.scheme.equals("rtmps", ignoreCase = true)
        ) {
            rtmpSource
        } else {
            defaultSource
        }
        return active.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active.read(buffer, offset, length)

    override fun getUri(): Uri? = active.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active.responseHeaders

    override fun close() {
        active.close()
    }
}
