package com.livestream.app.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.recyclerview.widget.LinearLayoutManager
import com.livestream.app.R
import com.livestream.app.data.model.ChannelItem
import com.livestream.app.data.model.ChannelStatus
import com.livestream.app.data.model.ExtractedStream
import com.livestream.app.data.repository.ChannelRepository
import com.livestream.app.databinding.ActivityMainBinding
import com.livestream.app.extractor.BackgroundStreamExtractor
import com.livestream.app.player.LivePlayerManager
import com.livestream.app.ui.adapter.ChannelsAdapter
import com.livestream.app.ui.dialog.EditChannelsBottomSheet
import com.livestream.app.ui.dialog.StreamDetailsBottomSheet

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var playerManager: LivePlayerManager
    private lateinit var extractor: BackgroundStreamExtractor

    private val channelsList = mutableListOf<ChannelItem>()
    private lateinit var channelsAdapter: ChannelsAdapter

    private var currentPlayingChannel: ChannelItem? = null
    private var currentExtractedStream: ExtractedStream? = null
    private var playerRetryCount = 0

    private var isFullscreen = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hidePlayerControls() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initChannels()
        initExtractor()
        initPlayer()
        setupListeners()
        setupBackHandling()

        // Auto-select and start first channel immediately
        if (channelsList.isNotEmpty()) {
            playChannel(channelsList[0])
        }
    }

    private fun initChannels() {
        channelsList.clear()
        channelsList.addAll(ChannelRepository.getChannels(this))

        channelsAdapter = ChannelsAdapter(channelsList) { selectedChannel ->
            playChannel(selectedChannel)
        }

        binding.rvChannels.layoutManager = LinearLayoutManager(this)
        binding.rvChannels.adapter = channelsAdapter
    }

    private fun initExtractor() {
        extractor = BackgroundStreamExtractor(this)

        // Pre-warm the cache for the second channel in background while first channel is played
        if (channelsList.size > 1) {
            extractor.preExtract(channelsList[1])
        }
    }

    private fun initPlayer() {
        playerManager = LivePlayerManager(
            context = this,
            playerView = binding.playerView,
            onPlaybackStateChange = { state, isPlaying ->
                updatePlaybackUi(state, isPlaying)
            },
            onError = { error ->
                handlePlayerError(error)
            }
        )
    }

    private fun setupListeners() {
        val controls = binding.playerControlsOverlay

        // Toggle controls overlay visibility on tapping player view or overlay background
        binding.playerView.setOnClickListener {
            togglePlayerControls()
        }
        controls.root.setOnClickListener {
            togglePlayerControls()
        }

        controls.btnPlayPause.setOnClickListener {
            playerManager.togglePlayPause()
            scheduleControlsAutoHide()
        }

        controls.btnReloadStream.setOnClickListener {
            currentPlayingChannel?.let { ch ->
                Toast.makeText(this, "Refreshing live stream…", Toast.LENGTH_SHORT).show()
                playChannel(ch, forceRefresh = true)
            }
            scheduleControlsAutoHide()
        }

        controls.btnAspectRatio.setOnClickListener {
            val modeName = playerManager.cycleAspectRatio()
            Toast.makeText(this, "Aspect Ratio: $modeName", Toast.LENGTH_SHORT).show()
            scheduleControlsAutoHide()
        }

        controls.btnStreamInfo.setOnClickListener {
            val stream = currentExtractedStream
            val ch = currentPlayingChannel
            if (stream != null && ch != null) {
                val dialog = StreamDetailsBottomSheet(ch.name, stream)
                dialog.show(supportFragmentManager, "StreamDetailsDialog")
            } else {
                Toast.makeText(this, "Stream details not available yet", Toast.LENGTH_SHORT).show()
            }
            scheduleControlsAutoHide()
        }

        controls.btnFullscreenToggle.setOnClickListener {
            toggleFullscreen()
            scheduleControlsAutoHide()
        }

        binding.btnEditChannels.setOnClickListener {
            val dialog = EditChannelsBottomSheet(channelsList) { updated ->
                channelsList.clear()
                channelsList.addAll(updated)
                channelsAdapter.notifyDataSetChanged()
                Toast.makeText(this, "Channels updated", Toast.LENGTH_SHORT).show()
            }
            dialog.show(supportFragmentManager, "EditChannelsDialog")
        }
    }

    /**
     * Plays the selected channel by extracting its live m3u8 in the background
     * and seamlessly starting ExoPlayer playback as soon as detected.
     */
    private fun playChannel(channel: ChannelItem, forceRefresh: Boolean = false) {
        currentPlayingChannel = channel
        channelsAdapter.setSelectedChannel(channel.id)

        // Update UI headers
        binding.playerControlsOverlay.tvPlayerChannelTitle.text = channel.name
        binding.tvHeaderTitle.text = "Now Playing: ${channel.name}"

        // Show loading progress
        binding.loadingStatusBox.visibility = View.VISIBLE
        binding.tvLoadingStatusText.text = "Extracting live stream in background…"
        binding.playerControlsOverlay.tvExtractorStatus.text = "⏳ Extracting m3u8 from embed…"
        channelsAdapter.updateChannelStatus(channel.id, ChannelStatus.EXTRACTING)

        extractor.extractStream(
            channel = channel,
            forceRefresh = forceRefresh,
            onSuccess = { stream ->
                currentExtractedStream = stream
                channel.extractedStreamUrl = stream.url
                channel.referer = stream.referer
                channel.lastExtractedTime = System.currentTimeMillis()

                binding.tvLoadingStatusText.text = "Stream captured! Starting live video…"
                binding.playerControlsOverlay.tvExtractorStatus.text = "🟢 Live HLS • ${channel.quality}"
                channelsAdapter.updateChannelStatus(channel.id, ChannelStatus.PLAYING)

                // Start native ExoPlayer playback immediately
                playerManager.playLiveStream(
                    streamUrl = stream.url,
                    referer = stream.referer,
                    userAgent = stream.userAgent
                )

                mainHandler.postDelayed({
                    binding.loadingStatusBox.visibility = View.GONE
                }, 1000)
            },
            onError = { err ->
                channelsAdapter.updateChannelStatus(channel.id, ChannelStatus.ERROR)
                binding.loadingStatusBox.visibility = View.GONE
                binding.playerControlsOverlay.tvExtractorStatus.text = "❌ Error: $err"
                Toast.makeText(this@MainActivity, "Extraction error: $err", Toast.LENGTH_LONG).show()
            }
        )

        scheduleControlsAutoHide()
    }

    private fun handlePlayerError(error: PlaybackException) {
        val channel = currentPlayingChannel ?: return
        Log.e("MainActivity", "Playback error for channel ${channel.name}: ${error.message}")

        if (playerRetryCount < 3) {
            playerRetryCount++
            extractor.invalidateCache(channel.id)
            binding.playerControlsOverlay.tvExtractorStatus.text = "⚠️ Stream dropped. Re-extracting ($playerRetryCount/3)…"
            mainHandler.postDelayed({
                if (currentPlayingChannel?.id == channel.id) {
                    playChannel(channel, forceRefresh = true)
                }
            }, 1500)
        } else {
            channelsAdapter.updateChannelStatus(channel.id, ChannelStatus.ERROR)
            binding.playerControlsOverlay.tvExtractorStatus.text = "❌ Playback failed. Tap Reload to retry."
            Toast.makeText(this, "Playback error on ${channel.name}. Tap Reload to try again.", Toast.LENGTH_LONG).show()
        }
    }

    private fun updatePlaybackUi(playbackState: Int, isPlaying: Boolean) {
        val controls = binding.playerControlsOverlay

        if (isPlaying) {
            controls.btnPlayPause.setImageResource(R.drawable.ic_pause)
        } else {
            controls.btnPlayPause.setImageResource(R.drawable.ic_play)
        }

        when (playbackState) {
            Player.STATE_BUFFERING -> {
                controls.playerLoadingProgress.visibility = View.VISIBLE
                controls.btnPlayPause.visibility = View.GONE
            }
            Player.STATE_READY -> {
                playerRetryCount = 0
                controls.playerLoadingProgress.visibility = View.GONE
                controls.btnPlayPause.visibility = View.VISIBLE
                binding.loadingStatusBox.visibility = View.GONE
            }
            Player.STATE_ENDED -> {
                controls.playerLoadingProgress.visibility = View.GONE
                controls.btnPlayPause.visibility = View.VISIBLE
            }
            Player.STATE_IDLE -> {
                controls.playerLoadingProgress.visibility = View.GONE
                controls.btnPlayPause.visibility = View.VISIBLE
            }
        }
    }

    private fun togglePlayerControls() {
        val overlay = binding.playerControlsOverlay.root
        if (overlay.visibility == View.VISIBLE) {
            hidePlayerControls()
        } else {
            showPlayerControls()
        }
    }

    private fun showPlayerControls() {
        binding.playerControlsOverlay.root.visibility = View.VISIBLE
        scheduleControlsAutoHide()
    }

    private fun hidePlayerControls() {
        binding.playerControlsOverlay.root.visibility = View.GONE
    }

    private fun scheduleControlsAutoHide() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.postDelayed(hideControlsRunnable, 3500)
    }

    private fun toggleFullscreen() {
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            mainHandler.postDelayed({
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }, 1000)
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyFullscreenLayout(landscape)
    }

    private fun applyFullscreenLayout(landscape: Boolean) {
        isFullscreen = landscape
        val controls = binding.playerControlsOverlay

        if (landscape) {
            binding.channelsHeaderBar.visibility = View.GONE
            binding.rvChannels.visibility = View.GONE

            val params = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
            params.dimensionRatio = null
            params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            params.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            binding.videoContainer.layoutParams = params

            controls.btnFullscreenToggle.setImageResource(R.drawable.ic_fullscreen_exit)
            hideSystemUi()
        } else {
            binding.channelsHeaderBar.visibility = View.VISIBLE
            binding.rvChannels.visibility = View.VISIBLE

            val params = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
            params.dimensionRatio = "16:9"
            params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            params.height = 0
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            binding.videoContainer.layoutParams = params

            controls.btnFullscreenToggle.setImageResource(R.drawable.ic_fullscreen)
            showSystemUi()
        }
    }

    private fun hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    private fun showSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFullscreen) {
                    toggleFullscreen()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        playerManager.resume()
    }

    override fun onPause() {
        super.onPause()
        playerManager.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(hideControlsRunnable)
        playerManager.release()
        extractor.destroy()
    }
}
