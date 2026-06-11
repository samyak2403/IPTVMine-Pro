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
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
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
import com.samyak.player.R


@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorTextView: TextView
    private lateinit var backBtn: ImageButton
    private lateinit var playPauseBtn: ImageButton
    private lateinit var fullScreenBtn: ImageButton
    private lateinit var repeatBtn: ImageButton
    private lateinit var prevBtn: ImageButton
    private lateinit var nextBtn: ImageButton
    private lateinit var videoTitle: TextView
    private lateinit var exoLiveText: TextView
    private lateinit var topController: LinearLayout
    private lateinit var bottomController: LinearLayout
    private lateinit var audioTrackBtn: ImageButton

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
    
    companion object {
        private const val TAG = "PlayerActivity"
        private const val INCREMENT_MILLIS = 5000L
        private const val PROGRESS_BAR_UPDATE_INTERVAL_MS = 16
        
        private const val SCALE_MODE_FIT = 0
        private const val SCALE_MODE_FILL = 1
        private const val SCALE_MODE_ZOOM = 2
        
        // PiP Actions
        private const val ACTION_PIP_PLAY = "com.samyak2403.iptvmine.PIP_PLAY"
        private const val ACTION_PIP_PAUSE = "com.samyak2403.iptvmine.PIP_PAUSE"
        private const val PIP_REQUEST_CODE = 101

        fun start(context: Context, name: String, streamUrl: String) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra("channel_name", name)
                putExtra("channel_stream_url", streamUrl)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        channelName = intent?.getStringExtra("channel_name")
        channelStreamUrl = intent?.getStringExtra("channel_stream_url")
        
        if (channelStreamUrl == null) {
            Log.e(TAG, "No channel stream url received")
            finish()
            return
        }

        // Detect TV mode
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        isTvMode = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        initializeSupportedFormats()
        initializeViews()
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
        progressBar = findViewById(R.id.progressBar)
        errorTextView = findViewById(R.id.errorTextView)

        backBtn = playerView.findViewById(R.id.backBtn)
        playPauseBtn = playerView.findViewById(R.id.playPauseBtn)
        fullScreenBtn = playerView.findViewById(R.id.fullScreenBtn)
        repeatBtn = playerView.findViewById(R.id.repeatBtn)
        prevBtn = playerView.findViewById(R.id.prevBtn)
        nextBtn = playerView.findViewById(R.id.nextBtn)
        videoTitle = playerView.findViewById(R.id.videoTitle)
        exoLiveText = playerView.findViewById(R.id.exo_live_text)
        topController = playerView.findViewById(R.id.topController)
        bottomController = playerView.findViewById(R.id.bottomController)
        audioTrackBtn = playerView.findViewById(R.id.audioTrackBtn)
        
        // Setup PiP button
        setupPipButton()
        
        // Setup audio track button
        setupAudioTrackButton()
        
        // Hide unnecessary buttons on TV
        if (isTvMode) {
            fullScreenBtn.visibility = View.GONE
            backBtn.visibility = View.GONE // Use remote back button instead
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

    private fun setupPlayer() {
        try {
            // Check data saving mode preference
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            isDataSavingEnabled = prefs.getBoolean("data_saving_enabled", false)
            
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("IPTVmine/1.0 (Android)")

            // Setup track selector with data saving constraints
            trackSelector = DefaultTrackSelector(this).apply {
                setParameters(
                    buildUponParameters()
                        .setMaxVideoSizeSd() // Limit to SD quality when data saving is enabled
                        .setMaxVideoBitrate(if (isDataSavingEnabled) 800_000 else Int.MAX_VALUE) // 800kbps for data saving
                        .setForceLowestBitrate(isDataSavingEnabled)
                )
            }

            player = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector!!)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .setHandleAudioBecomingNoisy(true)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(this)
                        .setDataSourceFactory(httpDataSourceFactory)
                )
                .build()

            playerView.player = player
            playerView.keepScreenOn = true
            playerView.controllerHideOnTouch = true
            playerView.controllerShowTimeoutMs = 3000
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)

            val mediaItem = detectAndCreateMediaItem(channelStreamUrl ?: "")

            // Create and store listener reference for proper cleanup
            playerListener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    this@PlayerActivity.playbackState = state
                    when (state) {
                        Player.STATE_READY -> {
                            this@PlayerActivity.isPlayerReady = true
                            progressBar.visibility = View.GONE
                            playerView.visibility = View.VISIBLE
                            startProgressBarUpdates()
                            updatePlayPauseButton(player?.isPlaying == true)
                            updateAudioTrackButtonVisibility()
                        }
                        Player.STATE_BUFFERING -> {
                            progressBar.visibility = View.VISIBLE
                        }
                        Player.STATE_ENDED -> {
                            updatePlayPauseButton(false)
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
                }
            }
            
            player?.apply {
                addListener(playerListener!!)
                setMediaItem(mediaItem)
                setPlayWhenReady(true)
                prepare()
            }

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
        } catch (e: Exception) {
            Log.e(TAG, "Error updating progress bar", e)
        }
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
            val url = channelStreamUrl?.lowercase() ?: ""
            isLiveStream = url.contains("live") || url.contains("24/7") ||
                    url.contains("stream") || url.contains("m3u8") ||
                    url.contains("mpd") || url.contains("dash")

            if (isLiveStream) {
                exoLiveText.visibility = View.VISIBLE
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

        isLiveStream = url.contains("live") || url.contains("24x7") ||
                url.contains("stream") || url.contains("real-time")

        if (isLiveStream) {
            Log.d(TAG, "Configuring for live streaming")
            builder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMaxPlaybackSpeed(1.02f)
                    .setTargetOffsetMs(5000)
                    .setMaxOffsetMs(10000)
                    .setMinOffsetMs(3000)
                    .setMinPlaybackSpeed(0.97f)
                    .build()
            )
        }

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
            cleartextError = error.cause?.cause?.message?.contains("Cleartext HTTP traffic") == true
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
            formatError = error.cause?.message?.let {
                it.contains("Format") || it.contains("No decoder")
            } == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for format error", e)
        }

        try {
            sourceError = error.errorCodeName == "ERROR_CODE_IO_UNSPECIFIED" ||
                    error.errorCodeName == "ERROR_CODE_IO_BAD_HTTP_STATUS"
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for source error", e)
        }

        when {
            cleartextError && channelStreamUrl?.startsWith("http://") == true -> {
                Log.d(TAG, "Attempting to use HTTPS instead of HTTP")
                val httpsUrl = tryHttpsUrl(channelStreamUrl ?: "")
                playStream(httpsUrl)
                return
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
        }

        errorTextView.visibility = View.VISIBLE
        playerView.visibility = View.GONE

        errorTextView.text = when {
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

    private fun tryAlternativeFormat(streamUrl: String) {
        val mimeTypesToTry = mutableListOf<String?>().apply {
            add(supportedFormats["m3u8"])
            add(supportedFormats["mpd"])
            add(supportedFormats["ts"])
            add(supportedFormats["mp4"])
            add(supportedFormats["webm"])
            add(supportedFormats["mkv"])
            add(supportedFormats["ism"])
            add(supportedFormats["flv"])
            add(null)
        }

        for ((index, mimeType) in mimeTypesToTry.withIndex()) {
            val builder = MediaItem.Builder().setUri(Uri.parse(streamUrl))
            mimeType?.let { builder.setMimeType(it) }

            try {
                Log.d(TAG, "Trying format ${mimeType ?: "auto-detect"} (attempt ${index + 1})")
                val mediaItem = builder.build()
                player?.apply {
                    clearMediaItems()
                    setMediaItem(mediaItem)
                    prepare()
                    play()
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    if (player?.isPlaying == true) {
                        Log.d(TAG, "Successfully playing with format ${mimeType ?: "auto-detect"}")
                    } else if (index < mimeTypesToTry.size - 1) {
                        Log.d(TAG, "Playback failed with ${mimeType ?: "auto-detect"}, trying next format")
                    }
                }, 2000)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Error with format ${mimeType ?: "auto-detect"}: ${e.message}")
            }
        }

        errorTextView.visibility = View.VISIBLE
        playerView.visibility = View.GONE
        errorTextView.text = "Unable to play this stream. Format not supported."
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
            registerReceiver(pipReceiver, filter, RECEIVER_NOT_EXPORTED)
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
}
