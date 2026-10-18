package com.livestream.app.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class LivePlayerManager(
    private val context: Context,
    private val playerView: PlayerView,
    private val onPlaybackStateChange: (state: Int, isPlaying: Boolean) -> Unit,
    private val onError: (error: PlaybackException) -> Unit
) {
    private val TAG = "LivePlayerManager"
    var exoPlayer: ExoPlayer? = null
        private set

    private var currentAspectRatioIndex = 0
    private val aspectRatios = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom"
    )

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2_000,   // Min buffer
                10_000,  // Max buffer
                1_000,   // Buffer for playback
                2_000    // Buffer for playback after rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        exoPlayer = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        playerView.player = exoPlayer
        playerView.resizeMode = aspectRatios[currentAspectRatioIndex].first

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val isPlaying = exoPlayer?.isPlaying == true
                onPlaybackStateChange(playbackState, isPlaying)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val state = exoPlayer?.playbackState ?: Player.STATE_IDLE
                onPlaybackStateChange(state, isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer Error: ${error.errorCodeName} - ${error.message}")
                onError(error)
            }
        })
    }

    /**
     * Prepares and starts playback of the extracted HLS (.m3u8) stream
     * with the appropriate Referer and User-Agent headers.
     */
    fun playLiveStream(streamUrl: String, referer: String, userAgent: String) {
        val player = exoPlayer ?: return

        try {
            Log.d(TAG, "Playing live stream: $streamUrl with Referer: $referer")

            val headers = mutableMapOf<String, String>()
            if (referer.isNotBlank()) {
                headers["Referer"] = referer
                try {
                    val uri = Uri.parse(referer)
                    if (uri.scheme != null && uri.host != null) {
                        val portSuffix = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
                        headers["Origin"] = "${uri.scheme}://${uri.host}$portSuffix"
                    }
                } catch (_: Exception) {}
            }

            val effectiveUa = if (userAgent.isNotBlank()) {
                userAgent
            } else {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            }

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(effectiveUa)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers)

            val hlsMediaSourceFactory = HlsMediaSource.Factory(httpDataSourceFactory)
                .setAllowChunklessPreparation(true)

            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setMaxPlaybackSpeed(1.05f)
                        .setMinPlaybackSpeed(0.95f)
                        .setTargetOffsetMs(3_000)
                        .build()
                )
                .build()

            val mediaSource = hlsMediaSourceFactory.createMediaSource(mediaItem)

            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start media source: ${e.message}")
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun stop() {
        exoPlayer?.stop()
    }

    fun cycleAspectRatio(): String {
        currentAspectRatioIndex = (currentAspectRatioIndex + 1) % aspectRatios.size
        val (mode, label) = aspectRatios[currentAspectRatioIndex]
        playerView.resizeMode = mode
        return label
    }

    fun release() {
        try {
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
        } catch (_: Exception) {}
    }
}
