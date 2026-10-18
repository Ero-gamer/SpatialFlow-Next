package com.codetrio.spatialflow.service

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow

class CrossfadePlayerManager(
    private val service: AudioPlaybackService,
    private val serviceScope: CoroutineScope,
    private val buildExoPlayer: (
        AudioPlaybackService.StereoBalanceProcessor,
        AudioPlaybackService.ReverbAudioProcessor,
        EightDAudioProcessor,
        com.codetrio.spatialflow.service.dac.AudioTelemetryProcessor,
        com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor
    ) -> ExoPlayer
) {
    private val TAG = "CrossfadePlayerManager"

    // --- Active Playback Properties ---
    var mainProcessor: AudioPlaybackService.StereoBalanceProcessor = service.StereoBalanceProcessor()
        private set
    var mainReverbProcessor: AudioPlaybackService.ReverbAudioProcessor = AudioPlaybackService.ReverbAudioProcessor()
        private set
    var mainEightDProcessor: EightDAudioProcessor = EightDAudioProcessor()
        private set
    var mainTelemetryProcessor: com.codetrio.spatialflow.service.dac.AudioTelemetryProcessor = com.codetrio.spatialflow.service.dac.AudioTelemetryProcessor()
        private set
    var mainTransitionFilter: com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor = com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor()
        private set

    var player: ExoPlayer = buildExoPlayer(mainProcessor, mainReverbProcessor, mainEightDProcessor, mainTelemetryProcessor, mainTransitionFilter)
        internal set

    @Volatile
    var isDspBypassed: Boolean = false
        set(value) {
            field = value
            mainTelemetryProcessor.isDspBypassed = value
            nextTelemetryProcessor?.isDspBypassed = value
            mainProcessor.enabled = !value
            nextProcessor?.enabled = !value
            mainTransitionFilter.isDspBypassed = value
            nextTransitionFilter?.isDspBypassed = value
            if (value) {
                // When DSP is bypassed (Bit-Perfect USB DAC), immediately unbind & release all AudioEffects
                effectsExecutor.execute {
                    releaseAllAudioEffects()
                    mainReverbProcessor.enabled = false
                    nextReverbProcessor?.enabled = false
                    mainEightDProcessor.enabled = false
                    nextEightDProcessor?.enabled = false
                    mainTransitionFilter.open()
                    nextTransitionFilter?.open()
                }
            }
        }

    var equalizerEffect: Equalizer? = null
    var bassBoostEffect: BassBoost? = null
    var loudnessEnhancer: LoudnessEnhancer? = null
    var virtualizerEffect: Virtualizer? = null
    var lastEffectsSessionId = -1

    // --- Standby Playback Properties ---
    var nextProcessor: AudioPlaybackService.StereoBalanceProcessor? = null
        private set
    var nextReverbProcessor: AudioPlaybackService.ReverbAudioProcessor? = null
        private set
    var nextEightDProcessor: EightDAudioProcessor? = null
        private set
    var nextTelemetryProcessor: com.codetrio.spatialflow.service.dac.AudioTelemetryProcessor? = null
        private set
    var nextTransitionFilter: com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor? = null
        private set
    var nextPlayer: ExoPlayer? = null
        internal set

    var nextEqualizerEffect: Equalizer? = null
    var nextBassBoostEffect: BassBoost? = null
    var nextLoudnessEnhancer: LoudnessEnhancer? = null
    var nextVirtualizerEffect: Virtualizer? = null

    // --- Crossfade States ---
    var isCrossfading = false
        internal set(value) {
            field = value
            com.codetrio.spatialflow.service.automix.AutoMixUiState.mixing.value = value
        }
    var crossfadeNextSongStarted = false
        private set
    var crossfadeDurationMs = 0
    var currentBaseVolume = 1.0f

    private var crossfadeJob: Job? = null
    private var nextPlayerListener: Player.Listener? = null
    private val effectsExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun set8DEnabled(enabled: Boolean) {
        if (isDspBypassed) {
            mainEightDProcessor.enabled = false
            nextEightDProcessor?.enabled = false
            return
        }
        mainEightDProcessor.enabled = enabled
        nextEightDProcessor?.enabled = enabled
    }

    fun set8DSpeed(speedHz: Float) {
        if (isDspBypassed) return
        mainEightDProcessor.rotationSpeedHz = speedHz
        nextEightDProcessor?.rotationSpeedHz = speedHz
    }

    fun set8DReverb(reverb: Float) {
        if (isDspBypassed) return
        mainEightDProcessor.roomReverbIntensity = reverb
        nextEightDProcessor?.roomReverbIntensity = reverb
    }

    private var currentPreparedMediaId: String? = null

    // --- Standby Preparation & Preload ---
    fun prepareNextPlayer(
        uri: Uri,
        title: String,
        artist: String,
        mediaId: String,
        viewModel: PlayerSharedViewModel?,
        cueTimeMs: Long = 0L,
        onNextPlayerError: (PlaybackExceptionWrapper) -> Unit
    ) {
        if (crossfadeDurationMs <= 0 && !com.codetrio.spatialflow.service.automix.AutoMixUiState.enabled.value) return

        if (nextPlayer != null && currentPreparedMediaId == mediaId) {
            if (cueTimeMs > 0L && nextPlayer?.currentPosition != cueTimeMs) {
                runCatching { nextPlayer?.seekTo(cueTimeMs) }
            }
            return
        }
        currentPreparedMediaId = mediaId

        if (nextPlayer == null) {
            nextProcessor = service.StereoBalanceProcessor()
            nextReverbProcessor = AudioPlaybackService.ReverbAudioProcessor()
            nextEightDProcessor = EightDAudioProcessor().apply {
                enabled = mainEightDProcessor.enabled
                rotationSpeedHz = mainEightDProcessor.rotationSpeedHz
                roomReverbIntensity = mainEightDProcessor.roomReverbIntensity
            }
            nextTelemetryProcessor = com.codetrio.spatialflow.service.dac.AudioTelemetryProcessor().apply {
                isDspBypassed = mainTelemetryProcessor.isDspBypassed
                activeDspList = mainTelemetryProcessor.activeDspList
            }
            nextTransitionFilter = com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor().apply {
                isDspBypassed = mainTransitionFilter.isDspBypassed
            }
            nextPlayer = buildExoPlayer(nextProcessor!!, nextReverbProcessor!!, nextEightDProcessor!!, nextTelemetryProcessor!!, nextTransitionFilter!!)
        }

        // Apply safety listener to standby player
        nextPlayerListener?.let {
            nextPlayer?.removeListener(it)
        }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "Crossfade player error: ${error.message}", error)
                onNextPlayerError(PlaybackExceptionWrapper(error))
            }
        }
        nextPlayerListener = listener
        nextPlayer?.addListener(listener)

        val item = service.buildMediaItem(uri, title, artist, mediaId)

        nextPlayer?.setMediaItem(item)
        if (cueTimeMs > 0L) {
            nextPlayer?.seekTo(cueTimeMs)
        }
        nextPlayer?.prepare()
        nextPlayer?.playWhenReady = false
        nextPlayer?.volume = 0f

        // Re-attach active hardware audio settings to standby player
        initializeNextAudioEffectsSafe(viewModel)
        
        // Match current speed/pitch settings
        viewModel?.let { vm ->
            val speed = vm.playbackSpeed.value
            val matchPitch = vm.isPitchMatched.value
            val params = PlaybackParameters(speed, if (matchPitch) 1.0f else speed)
            nextPlayer?.playbackParameters = params
        }

        Log.d(TAG, "⚡ SUCCESSFULLY PREPARED STANDBY PLAYER FOR OVERLAP TRANSITION")
    }

    // --- Synchronized Effect Handlers ---
    fun setEqualizerEnabled(enabled: Boolean, viewModel: PlayerSharedViewModel?) {
        effectsExecutor.execute {
            if (isDspBypassed) {
                try { equalizerEffect?.enabled = false } catch (_: Exception) {}
                try { nextEqualizerEffect?.enabled = false } catch (_: Exception) {}
                releaseAllAudioEffects()
                return@execute
            }
            try {
                equalizerEffect?.enabled = enabled
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle equalizer: ${e.message}")
            }
            try {
                nextEqualizerEffect?.enabled = enabled
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle standby equalizer: ${e.message}")
            }
            applyEffectsFromViewModelAsync(viewModel)
        }
    }

    fun setEqBandGain(bandIndex: Int, gainDb: Int) {
        if (isDspBypassed) return
        effectsExecutor.execute {
            try {
                equalizerEffect?.setBandLevel(bandIndex.toShort(), (gainDb * 100).toShort())
            } catch (_: Exception) {}
            try {
                nextEqualizerEffect?.setBandLevel(bandIndex.toShort(), (gainDb * 100).toShort())
            } catch (_: Exception) {}
        }
    }

    fun setLoudnessEnabled(enabled: Boolean) {
        effectsExecutor.execute {
            if (isDspBypassed) {
                try { loudnessEnhancer?.enabled = false } catch (_: Exception) {}
                try { nextLoudnessEnhancer?.enabled = false } catch (_: Exception) {}
                return@execute
            }
            try { loudnessEnhancer?.enabled = enabled } catch (_: Exception) {}
            try { nextLoudnessEnhancer?.enabled = enabled } catch (_: Exception) {}
        }
    }

    fun setLoudnessGain(gainDb: Int) {
        if (isDspBypassed) return
        effectsExecutor.execute {
            try { loudnessEnhancer?.setTargetGain(gainDb * 1000) } catch (_: Exception) {}
            try { nextLoudnessEnhancer?.setTargetGain(gainDb * 1000) } catch (_: Exception) {}
        }
    }

    fun setPlaybackSpeed(speed: Float, matchPitch: Boolean) {
        val params = PlaybackParameters(speed, if (matchPitch) 1.0f else speed)
        player.playbackParameters = params
        nextPlayer?.playbackParameters = params
    }

    fun setBalance(balanceValue: Float) {
        val balance = balanceValue / 50.0f
        mainProcessor.setBalance(balance)
        nextProcessor?.setBalance(balance)
    }

    // --- Hardware Effects Lifecycle Management ---
    fun initializeAudioEffectsSafe(viewModel: PlayerSharedViewModel?) {
        if (isDspBypassed) {
            effectsExecutor.execute {
                releaseAudioEffectsOnly()
            }
            return
        }
        val sessionId = player.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return

        effectsExecutor.execute {
            try {
                if (isDspBypassed) {
                    releaseAudioEffectsOnly()
                    return@execute
                }
                if (sessionId == lastEffectsSessionId && equalizerEffect != null) {
                    return@execute
                }
                releaseAudioEffectsOnly()

                equalizerEffect = Equalizer(0, sessionId)
                bassBoostEffect = BassBoost(0, sessionId)
                loudnessEnhancer = LoudnessEnhancer(sessionId)
                try {
                    val isAtmos = com.codetrio.spatialflow.audio.dolby.DolbyAtmosManager.isAtmosActive.value
                    virtualizerEffect = Virtualizer(0, sessionId).apply {
                        if (strengthSupported) {
                            setStrength(1000.toShort())
                        }
                        enabled = isAtmos
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Virtualizer not supported on this device/session: ${e.message}")
                }
                lastEffectsSessionId = sessionId

                applyEffectsFromViewModelAsync(viewModel)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to init active audio effects: ${ex.message}")
            }
        }
    }

    private fun initializeNextAudioEffectsSafe(viewModel: PlayerSharedViewModel?) {
        if (isDspBypassed) {
            effectsExecutor.execute {
                releaseNextAudioEffectsOnly()
            }
            return
        }
        val nextP = nextPlayer ?: return
        val sessionId = nextP.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return

        effectsExecutor.execute {
            try {
                if (isDspBypassed) {
                    releaseNextAudioEffectsOnly()
                    return@execute
                }
                releaseNextAudioEffectsOnly()

                nextEqualizerEffect = Equalizer(0, sessionId)
                nextBassBoostEffect = BassBoost(0, sessionId)
                nextLoudnessEnhancer = LoudnessEnhancer(sessionId)
                try {
                    val isAtmos = com.codetrio.spatialflow.audio.dolby.DolbyAtmosManager.isAtmosActive.value
                    nextVirtualizerEffect = Virtualizer(0, sessionId).apply {
                        if (strengthSupported) {
                            setStrength(1000.toShort())
                        }
                        enabled = isAtmos
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Standby Virtualizer not supported: ${e.message}")
                }

                applyEffectsFromViewModelAsync(viewModel)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to init next audio effects: ${ex.message}")
            }
        }
    }

    fun applyEffectsFromViewModelAsync(viewModel: PlayerSharedViewModel?) {
        effectsExecutor.execute {
            if (isDspBypassed) {
                releaseAllAudioEffects()
                mainReverbProcessor.enabled = false
                nextReverbProcessor?.enabled = false
                mainEightDProcessor.enabled = false
                nextEightDProcessor?.enabled = false
                return@execute
            }
            val vm = viewModel ?: return@execute
            // Apply Equalizer
            try {
                if (vm.isEqualizerEnabled.value) {
                    equalizerEffect?.enabled = true
                    nextEqualizerEffect?.enabled = true
                    setEqBandGain(0, vm.eqBand1.value)
                    setEqBandGain(1, vm.eqBand2.value)
                    setEqBandGain(2, vm.eqBand3.value)
                    setEqBandGain(3, vm.eqBand4.value)
                    setEqBandGain(4, vm.eqBand5.value)
                } else {
                    equalizerEffect?.enabled = false
                    nextEqualizerEffect?.enabled = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply Equalizer state: ${e.message}")
            }

            // Apply Reverb
            try {
                val isEnabled = vm.isReverbEnabled.value
                val intensity = vm.reverbLevel.value.coerceIn(0f, 1f)
                val preset = vm.reverbPreset.value

                mainReverbProcessor.enabled = isEnabled
                mainReverbProcessor.intensity = intensity
                mainReverbProcessor.preset = preset.toInt()

                nextReverbProcessor?.let {
                    it.enabled = isEnabled
                    it.intensity = intensity
                    it.preset = preset.toInt()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply Reverb state: ${e.message}")
            }

            // Apply Loudness Enhancer
            try {
                if (vm.isLoudnessEnabled.value) {
                    loudnessEnhancer?.enabled = true
                    nextLoudnessEnhancer?.enabled = true
                    setLoudnessGain(vm.loudnessGain.value)
                } else if (vm.isEqualizerEnabled.value) {
                    loudnessEnhancer?.enabled = true
                    nextLoudnessEnhancer?.enabled = true
                    try { loudnessEnhancer?.setTargetGain(600) } catch (_: Exception) {}
                    try { nextLoudnessEnhancer?.setTargetGain(600) } catch (_: Exception) {}
                } else {
                    loudnessEnhancer?.enabled = false
                    nextLoudnessEnhancer?.enabled = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply Loudness state: ${e.message}")
            }
        }
    }

    private fun releaseAudioEffectsOnly() {
        try { equalizerEffect?.enabled = false; equalizerEffect?.release() } catch (_: Exception) {}
        equalizerEffect = null
        try { bassBoostEffect?.enabled = false; bassBoostEffect?.release() } catch (_: Exception) {}
        bassBoostEffect = null
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.setTargetGain(0)
            loudnessEnhancer?.release()
        } catch (_: Exception) {}
        loudnessEnhancer = null
        try { virtualizerEffect?.enabled = false; virtualizerEffect?.release() } catch (_: Exception) {}
        virtualizerEffect = null
        mainReverbProcessor.clearBuffers()
    }

    private fun releaseNextAudioEffectsOnly() {
        try { nextEqualizerEffect?.enabled = false; nextEqualizerEffect?.release() } catch (_: Exception) {}
        nextEqualizerEffect = null
        try { nextBassBoostEffect?.enabled = false; nextBassBoostEffect?.release() } catch (_: Exception) {}
        nextBassBoostEffect = null
        try {
            nextLoudnessEnhancer?.enabled = false
            nextLoudnessEnhancer?.setTargetGain(0)
            nextLoudnessEnhancer?.release()
        } catch (_: Exception) {}
        nextLoudnessEnhancer = null
        try { nextVirtualizerEffect?.enabled = false; nextVirtualizerEffect?.release() } catch (_: Exception) {}
        nextVirtualizerEffect = null
        nextReverbProcessor?.clearBuffers()
    }

    /**
     * Completely strips and destroys all audio effects, boosters, and DSP processors
     * when a USB DAC is connected, ensuring 100% pure bit-exact music playout.
     */
    fun stripAllEffectsForDac() {
        isDspBypassed = true
        effectsExecutor.execute {
            releaseAudioEffectsOnly()
            releaseNextAudioEffectsOnly()
            mainReverbProcessor.enabled = false
            nextReverbProcessor?.enabled = false
            mainEightDProcessor.enabled = false
            nextEightDProcessor?.enabled = false
            mainProcessor.enabled = false
            nextProcessor?.enabled = false
            try { player.volume = 1.0f } catch (_: Exception) {}
            try { nextPlayer?.volume = 1.0f } catch (_: Exception) {}
        }
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        effectsExecutor.execute {
            if (isDspBypassed) {
                try { virtualizerEffect?.enabled = false } catch (_: Exception) {}
                try { nextVirtualizerEffect?.enabled = false } catch (_: Exception) {}
                return@execute
            }
            try {
                virtualizerEffect?.enabled = enabled
                if (enabled && virtualizerEffect?.strengthSupported == true) {
                    virtualizerEffect?.setStrength(1000.toShort())
                }
            } catch (_: Exception) {}
            try {
                nextVirtualizerEffect?.enabled = enabled
                if (enabled && nextVirtualizerEffect?.strengthSupported == true) {
                    nextVirtualizerEffect?.setStrength(1000.toShort())
                }
            } catch (_: Exception) {}
        }
    }

    fun releaseAllAudioEffects() {
        releaseAudioEffectsOnly()
        releaseNextAudioEffectsOnly()
    }

    // --- Coroutine Equal-Power & AutoMix Crossfader Transition ---
    fun startCrossfade(
        viewModel: PlayerSharedViewModel?,
        plan: com.codetrio.spatialflow.service.automix.AutoMixPlan? = null,
        onSongTransition: (Int) -> Unit,
        onComplete: () -> Unit
    ) {
        if (isCrossfading || nextPlayer == null) return
        isCrossfading = true
        crossfadeNextSongStarted = true

        val incomingPlayer = nextPlayer!!
        incomingPlayer.volume = 0f

        val smartPlan = plan
        val isSmart = smartPlan != null
        if (smartPlan != null) {
            if (smartPlan.incomingCueMs > 0L && incomingPlayer.currentPosition < smartPlan.incomingCueMs - 200L) {
                incomingPlayer.seekTo(smartPlan.incomingCueMs)
            }
            val userSpeed = player.playbackParameters.speed
            val newSpeed = (userSpeed.toDouble() * smartPlan.incomingPlaybackRate).coerceIn(0.05, 8.0).toFloat()
            incomingPlayer.playbackParameters = player.playbackParameters.withSpeed(newSpeed)
        } else {
            incomingPlayer.playbackParameters = player.playbackParameters
        }
        com.codetrio.spatialflow.service.automix.AutoMixUiState.mixing.value = true

        // In Bit-Perfect mode (USB DAC only), software volume must not touch PCM samples.
        // For wireless (Bluetooth) devices, crossfade operates normally.
        if (com.codetrio.spatialflow.util.QualityModeSelector.isBitPerfectActive(service)) {
            com.codetrio.spatialflow.service.automix.AutoMixUiState.mixing.value = false
            incomingPlayer.volume = 1.0f
            player.volume = 1.0f
            val nextIdx = viewModel?.getNextSongIndex() ?: -1
            onSongTransition(nextIdx)
            onComplete()
            return
        }

        // 2. Launch coroutine crossfade loop
        crossfadeJob?.cancel()
        crossfadeJob = serviceScope.launch(Dispatchers.Main) {
            val planFadeMs = plan?.fadeMs ?: crossfadeDurationMs.toLong()
            var fadeMs = planFadeMs.coerceAtLeast(1000L)

            // Re-clamp fadeMs if outgoing track has less duration remaining
            val outgoingDuration = player.duration
            if (outgoingDuration != C.TIME_UNSET && outgoingDuration > 0L) {
                val leftMs = outgoingDuration - player.currentPosition - if (isSmart) 0L else 150L
                if (leftMs < fadeMs) {
                    fadeMs = leftMs.coerceAtLeast(500L)
                }
            }

            // Buffer readiness check (5000ms timeout)
            val requiredBufferedMs = (fadeMs + 5000L).coerceIn(5000L, 12500L)
            if (!awaitCrossfadePlayerReady(incomingPlayer, 5000L, requiredBufferedMs)) {
                Log.w(TAG, "Standby player not ready in time; aborting crossfade")
                cancelCrossfade()
                return@launch
            }

            incomingPlayer.playWhenReady = true
            incomingPlayer.play()

            // Await incoming audio advancement to prevent silent dips or unbuffered pops
            val advanced = awaitAudioPositionAdvancement(incomingPlayer, 2000L)
            if (!advanced) {
                Log.w(TAG, "Standby player failed to advance audio; aborting crossfade")
                cancelCrossfade()
                return@launch
            }

            var elapsedMs = 0L
            var lastTickMs = android.os.SystemClock.elapsedRealtime()
            val blendStartOutgoingPositionMs = if (isSmart) player.currentPosition else 0L

            while (isActive && elapsedMs < fadeMs) {
                val outDur = player.duration
                val outgoingPausedAtEnd = player.playbackState == Player.STATE_ENDED ||
                    (!player.playWhenReady &&
                     player.playbackState == Player.STATE_READY &&
                     outDur != C.TIME_UNSET &&
                     player.currentPosition >= outDur - 250L)

                if (outgoingPausedAtEnd) {
                    player.volume = 0f
                    incomingPlayer.volume = currentBaseVolume
                    break
                }

                // If both players are paused, suspend crossfade progress and wait
                if (!player.isPlaying && !incomingPlayer.isPlaying) {
                    delay(50L)
                    lastTickMs = android.os.SystemClock.elapsedRealtime()
                    continue
                }

                val nowMs = android.os.SystemClock.elapsedRealtime()
                if (isSmart) {
                    elapsedMs = (player.currentPosition - blendStartOutgoingPositionMs).coerceIn(0L, fadeMs)
                } else {
                    elapsedMs = (elapsedMs + (nowMs - lastTickMs)).coerceAtMost(fadeMs)
                }
                lastTickMs = nowMs

                val progress = (elapsedMs.toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)

                // Gain curves: equal-power cos for outgoing; gentle sin^2 for smart automix, sin for standard crossfade
                player.volume = com.codetrio.spatialflow.service.automix.CrossfadePolicy.outgoingVolume(progress, currentBaseVolume)
                incomingPlayer.volume = if (isSmart) {
                    com.codetrio.spatialflow.service.automix.CrossfadePolicy.gentleIncomingVolume(progress, currentBaseVolume)
                } else {
                    com.codetrio.spatialflow.service.automix.CrossfadePolicy.incomingVolume(progress, currentBaseVolume)
                }

                if (smartPlan != null) {
                    rideAutoMixFilters(smartPlan, progress)
                }

                delay(32L)
            }

            // AutoMix Outgoing Tail Wait: keep outgoing playing quietly until its natural ending
            if (isSmart) {
                val tailDeadlineMs = android.os.SystemClock.elapsedRealtime() + 2000L
                val outDur = player.duration
                while (isActive && player.playbackState != Player.STATE_ENDED) {
                    if (outDur <= 0L || C.TIME_UNSET == outDur) break
                    if (player.currentPosition >= outDur - 8L) break
                    if (android.os.SystemClock.elapsedRealtime() >= tailDeadlineMs) break

                    player.volume = 0f
                    incomingPlayer.volume = currentBaseVolume
                    delay(32L)
                }
            }

            // Shift viewModel song pointer index and notification at promotion/handoff
            val nextIdx = viewModel?.getNextSongIndex() ?: -1
            onSongTransition(nextIdx)

            onComplete()
        }
    }

    private fun hasBufferedForSmoothStart(
        targetPlayer: ExoPlayer,
        minimumBufferedMs: Long,
    ): Boolean {
        if (minimumBufferedMs <= 0L) return true
        val duration = targetPlayer.duration
        val currentPosition = targetPlayer.currentPosition.coerceAtLeast(0L)
        val remainingDuration =
            if (duration != C.TIME_UNSET && duration > currentPosition) {
                duration - currentPosition
            } else {
                Long.MAX_VALUE
            }
        val requiredBufferedMs = minimumBufferedMs.coerceAtMost(remainingDuration)
        if (requiredBufferedMs <= 0L) return true

        val bufferedDuration = targetPlayer.totalBufferedDuration.coerceAtLeast(0L)
        if (bufferedDuration >= requiredBufferedMs) return true

        return duration != C.TIME_UNSET &&
            targetPlayer.bufferedPosition >= duration - 150L
    }

    private suspend fun awaitCrossfadePlayerReady(
        crossfadePlayer: ExoPlayer,
        timeoutMs: Long,
        minimumBufferedMs: Long,
    ): Boolean {
        val deadlineMs = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (serviceScope.isActive && android.os.SystemClock.elapsedRealtime() < deadlineMs) {
            val snapshot =
                com.codetrio.spatialflow.service.automix.CrossfadePolicy.ReadinessSnapshot(
                    isReady = crossfadePlayer.playbackState == Player.STATE_READY,
                    isIdle = crossfadePlayer.playbackState == Player.STATE_IDLE,
                    isEnded = crossfadePlayer.playbackState == Player.STATE_ENDED,
                    hasError = crossfadePlayer.playerError != null,
                    bufferedEnough = hasBufferedForSmoothStart(crossfadePlayer, minimumBufferedMs),
                )
            when {
                com.codetrio.spatialflow.service.automix.CrossfadePolicy.mustAbortReadiness(snapshot) -> return false
                com.codetrio.spatialflow.service.automix.CrossfadePolicy.isReadyForFadeStart(snapshot) -> return true
                snapshot.isIdle -> crossfadePlayer.prepare()
            }
            delay(50L)
        }
        return crossfadePlayer.playbackState == Player.STATE_READY &&
            crossfadePlayer.playerError == null &&
            hasBufferedForSmoothStart(crossfadePlayer, minimumBufferedMs)
    }

    private suspend fun awaitAudioPositionAdvancement(
        targetPlayer: ExoPlayer,
        timeoutMs: Long = 2000L
    ): Boolean {
        val deadlineMs = android.os.SystemClock.elapsedRealtime() + timeoutMs
        var lastPositionMs = targetPlayer.currentPosition
        while (serviceScope.isActive && android.os.SystemClock.elapsedRealtime() < deadlineMs) {
            if (targetPlayer.playerError != null) return false
            delay(80L)
            val positionMs = targetPlayer.currentPosition
            if (com.codetrio.spatialflow.service.automix.CrossfadePolicy.hasAudioAdvanced(
                    com.codetrio.spatialflow.service.automix.CrossfadePolicy.AudioAdvancementSnapshot(
                        isReady = targetPlayer.playbackState == Player.STATE_READY,
                        isPlaying = targetPlayer.isPlaying,
                        hasError = targetPlayer.playerError != null,
                        positionMs = positionMs,
                        previousPositionMs = lastPositionMs
                    )
                )
            ) {
                return true
            }
            lastPositionMs = positionMs
        }
        return false
    }

    private fun hasMixChoreography(plan: com.codetrio.spatialflow.service.automix.AutoMixPlan?): Boolean {
        if (plan == null) return false
        return plan.style == com.codetrio.spatialflow.service.automix.AutoMixStyle.DJ_BLEND ||
            plan.style == com.codetrio.spatialflow.service.automix.AutoMixStyle.DJ_FILTER ||
            plan.incomingCueMs > 0L ||
            kotlin.math.abs(plan.incomingPlaybackRate - 1.0) > 0.01
    }

    private fun rideAutoMixFilters(
        plan: com.codetrio.spatialflow.service.automix.AutoMixPlan,
        progress: Float
    ) {
        val outgoing = mainTransitionFilter
        val incoming = nextTransitionFilter ?: return
        when (plan.style) {
            com.codetrio.spatialflow.service.automix.AutoMixStyle.DJ_FILTER -> rideSweepOut(plan, progress, outgoing, incoming)
            com.codetrio.spatialflow.service.automix.AutoMixStyle.DJ_BLEND ->
                if (plan.bassSwap) {
                    rideBassHandover(plan, progress, outgoing, incoming)
                } else {
                    rideClashSeparation(plan, progress, outgoing, incoming)
                }

            else -> {
                outgoing.open()
                incoming.open()
            }
        }
    }

    private fun rideSweepOut(
        plan: com.codetrio.spatialflow.service.automix.AutoMixPlan,
        progress: Float,
        outgoing: com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor,
        incoming: com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor
    ) {
        val sweep = plan.filterSweep.coerceIn(0.0, 1.0)
        if (sweep <= 0.0) {
            outgoing.open()
            incoming.open()
            return
        }
        val open = com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor.OPEN_HZ.toDouble()
        val floor = automixGlide(open, AUTO_MIX_SWEEP_FLOOR_HZ, sweep)
        val shaped = progress.toDouble().pow(AUTO_MIX_SWEEP_SHAPE)
        outgoing.setCutoffs(automixGlide(open, floor, shaped), com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor.OFF_HZ.toDouble())

        val relax = (1.0 - progress / AUTO_MIX_ENTRY_OPEN_BY).coerceIn(0.0, 1.0)
        val entryTop = automixGlide(open, AUTO_MIX_SWEEP_ENTRY_HIGH_HZ, sweep)
        incoming.setCutoffs(
            open,
            automixGlide(com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor.OFF_HZ.toDouble(), entryTop, sweep * relax)
        )
    }

    private fun rideBassHandover(
        plan: com.codetrio.spatialflow.service.automix.AutoMixPlan,
        progress: Float,
        outgoing: com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor,
        incoming: com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor
    ) {
        val swapAt = plan.bassSwapFraction.coerceIn(0.05, 0.95)
        val handover = ((progress - swapAt) / 0.10 * 0.5 + 0.5).coerceIn(0.0, 1.0)
        val clash = plan.vocalOverlap.coerceIn(0.0, 1.0)

        val clashTop = automixGlide(AUTO_MIX_HANDOVER_ENTRY_HZ, AUTO_MIX_HANDOVER_CLASH_HZ, clash)
        val relax = (1.0 - progress / AUTO_MIX_HANDOVER_OPEN_BY).coerceIn(0.0, 1.0)
        val highEntry = automixGlide(com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor.OFF_HZ.toDouble(), clashTop, relax)
        val highFloor = automixGlide(com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor.OFF_HZ.toDouble(), AUTO_MIX_BASS_HANDOVER_HZ, 1.0 - handover)
        incoming.setCutoffs(
            com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor.OPEN_HZ.toDouble(),
            maxOf(highEntry, highFloor)
        )

        val exitFrom = automixGlide(AUTO_MIX_EXIT_FROM_LATE, AUTO_MIX_EXIT_FROM_EARLY, clash)
        val exitAmount = ((progress - exitFrom) / (1.0 - exitFrom)).coerceIn(0.0, 1.0)
        val exitFloor = automixGlide(AUTO_MIX_EXIT_LOW_HZ, AUTO_MIX_EXIT_CLASH_LOW_HZ, clash)
        outgoing.setCutoffs(
            automixGlide(com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor.OPEN_HZ.toDouble(), exitFloor, exitAmount),
            automixGlide(com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor.OFF_HZ.toDouble(), AUTO_MIX_BASS_HANDOVER_HZ, handover)
        )
    }

    private fun rideClashSeparation(
        plan: com.codetrio.spatialflow.service.automix.AutoMixPlan,
        progress: Float,
        outgoing: com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor,
        incoming: com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor
    ) {
        val amount = plan.vocalOverlap.coerceIn(0.0, 1.0)
        if (amount <= 0.0) {
            outgoing.open()
            incoming.open()
            return
        }
        val open = com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor.OPEN_HZ.toDouble()
        val floor = automixGlide(open, AUTO_MIX_CLASH_FLOOR_HZ, amount)
        outgoing.setCutoffs(
            automixGlide(open, floor, progress.toDouble().pow(AUTO_MIX_SWEEP_SHAPE)),
            com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor.OFF_HZ.toDouble()
        )
        val relax = (1.0 - progress / AUTO_MIX_HANDOVER_OPEN_BY).coerceIn(0.0, 1.0)
        incoming.setCutoffs(
            open,
            automixGlide(com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor.OFF_HZ.toDouble(), AUTO_MIX_CLASH_HIGH_HZ, amount * relax)
        )
    }

    private fun automixGlide(
        from: Double,
        to: Double,
        amount: Double
    ): Double = from * (to / from).pow(amount.coerceIn(0.0, 1.0))

    fun completeCrossfadeHandoff(
        viewModel: PlayerSharedViewModel?,
        onPromotionComplete: (ExoPlayer) -> Unit
    ) {
        crossfadeJob = null
        val oldPlayer = player
        val newPlayer = nextPlayer ?: return

        player = newPlayer
        
        // Swap processors
        mainProcessor = nextProcessor ?: service.StereoBalanceProcessor()
        mainReverbProcessor = nextReverbProcessor ?: AudioPlaybackService.ReverbAudioProcessor()
        mainEightDProcessor = nextEightDProcessor ?: EightDAudioProcessor()
        nextTelemetryProcessor?.let { mainTelemetryProcessor = it }
        mainTransitionFilter = nextTransitionFilter ?: com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor()
        mainTransitionFilter.open()

        // Remove safety listener from promoted player
        nextPlayerListener?.let {
            newPlayer.removeListener(it)
        }
        nextPlayerListener = null

        // Stop and release old player
        oldPlayer.stop()
        oldPlayer.release()

        // Re-initialize standby player container
        nextProcessor = service.StereoBalanceProcessor()
        nextReverbProcessor = AudioPlaybackService.ReverbAudioProcessor()
        nextEightDProcessor = EightDAudioProcessor().apply {
            enabled = mainEightDProcessor.enabled
            rotationSpeedHz = mainEightDProcessor.rotationSpeedHz
            roomReverbIntensity = mainEightDProcessor.roomReverbIntensity
        }
        nextTelemetryProcessor = com.codetrio.spatialflow.service.dac.AudioTelemetryProcessor().apply {
            isDspBypassed = mainTelemetryProcessor.isDspBypassed
            activeDspList = mainTelemetryProcessor.activeDspList
        }
        nextTransitionFilter = com.codetrio.spatialflow.service.automix.AutoMixFilterProcessor().apply {
            isDspBypassed = mainTransitionFilter.isDspBypassed
        }
        nextPlayer = buildExoPlayer(nextProcessor!!, nextReverbProcessor!!, nextEightDProcessor!!, nextTelemetryProcessor!!, nextTransitionFilter!!)

        // Reset speed to user setting
        viewModel?.let { vm ->
            val speed = vm.playbackSpeed.value
            val matchPitch = vm.isPitchMatched.value
            player.playbackParameters = PlaybackParameters(speed, if (matchPitch) 1.0f else speed)
        }

        // Apply hardware audio configurations to new active player session ID
        initializeAudioEffectsSafe(viewModel)

        isCrossfading = false
        crossfadeNextSongStarted = false
        currentPreparedMediaId = null
        com.codetrio.spatialflow.service.automix.AutoMixUiState.mixing.value = false
        com.codetrio.spatialflow.service.automix.AutoMixUiState.transitionWindow.value = null

        onPromotionComplete(newPlayer)
    }

    fun cancelCrossfade() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        
        nextPlayerListener?.let {
            nextPlayer?.removeListener(it)
        }
        nextPlayerListener = null

        nextPlayer?.stop()
        nextPlayer?.release()
        nextPlayer = null
        currentPreparedMediaId = null
        nextTelemetryProcessor = null
        nextProcessor = null
        nextReverbProcessor = null
        nextEightDProcessor = null
        nextTransitionFilter?.open()
        nextTransitionFilter = null
        mainTransitionFilter.open()
        releaseNextAudioEffectsOnly()

        isCrossfading = false
        crossfadeNextSongStarted = false
        player.pauseAtEndOfMediaItems = false
        player.volume = currentBaseVolume
        com.codetrio.spatialflow.service.automix.AutoMixUiState.mixing.value = false
        com.codetrio.spatialflow.service.automix.AutoMixUiState.transitionWindow.value = null
    }

    companion object {
        private const val AUTO_MIX_SWEEP_SHAPE = 0.75
        private const val AUTO_MIX_SWEEP_ENTRY_HIGH_HZ = 7_000.0
        private const val AUTO_MIX_SWEEP_FLOOR_HZ = 300.0
        private const val AUTO_MIX_ENTRY_OPEN_BY = 0.6
        private const val AUTO_MIX_HANDOVER_ENTRY_HZ = 520.0
        private const val AUTO_MIX_HANDOVER_CLASH_HZ = 950.0
        private const val AUTO_MIX_HANDOVER_OPEN_BY = 0.45
        private const val AUTO_MIX_BASS_HANDOVER_HZ = 200.0
        private const val AUTO_MIX_EXIT_FROM_LATE = 0.30
        private const val AUTO_MIX_EXIT_FROM_EARLY = 0.12
        private const val AUTO_MIX_EXIT_LOW_HZ = 2_200.0
        private const val AUTO_MIX_EXIT_CLASH_LOW_HZ = 1_100.0
        private const val AUTO_MIX_CLASH_FLOOR_HZ = 1_600.0
        private const val AUTO_MIX_CLASH_HIGH_HZ = 700.0
    }

    fun release() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        
        player.release()
        
        nextPlayerListener?.let {
            nextPlayer?.removeListener(it)
        }
        nextPlayerListener = null
        nextPlayer?.release()
        
        releaseAllAudioEffects()
        effectsExecutor.shutdownNow()
    }
}

// Custom wrapper to bridge Media3 exception to manager callback
class PlaybackExceptionWrapper(val exception: androidx.media3.common.PlaybackException)
