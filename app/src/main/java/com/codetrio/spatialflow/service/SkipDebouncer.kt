package com.codetrio.spatialflow.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Rapid Skip Debouncer & Coalescer.
 * When the user taps skip rapidly, immediately provides visual feedback (0ms UI update)
 * for the target index while canceling intermediate network/playback loads, and commits
 * playback only when rapid skip gestures pause.
 */
class SkipDebouncer(
    private val scope: CoroutineScope,
    private val debounceMs: Long = 150L,
    private val onInstantMetadataUpdate: (targetIndex: Int) -> Unit,
    private val onCancelInFlightLoads: () -> Unit,
    private val onCommitSkip: (finalTargetIndex: Int) -> Unit
) {
    private var debounceJob: Job? = null
    private var pendingTargetIndex: Int? = null

    val isSkipping: Boolean get() = debounceJob?.isActive == true

    fun skipTo(targetIndex: Int, maxQueueSize: Int) {
        if (maxQueueSize <= 0) return
        val validIndex = targetIndex.coerceIn(0, maxQueueSize - 1)

        pendingTargetIndex = validIndex

        // 1. Instant 0ms visual feedback
        onInstantMetadataUpdate(validIndex)

        // 2. Cancel in-flight network/media loads for intermediate skipped tracks
        onCancelInFlightLoads()

        // 3. Debounce final audio playback seek/load trigger
        debounceJob?.cancel()
        debounceJob = scope.launch(Dispatchers.Main) {
            delay(debounceMs)
            val indexToPlay = pendingTargetIndex ?: validIndex
            pendingTargetIndex = null
            onCommitSkip(indexToPlay)
        }
    }

    fun skipNext(currentIndex: Int, maxQueueSize: Int, getNextIndex: (Int) -> Int) {
        val baseIndex = pendingTargetIndex ?: currentIndex
        val target = getNextIndex(baseIndex)
        skipTo(target, maxQueueSize)
    }

    fun skipPrevious(currentIndex: Int, maxQueueSize: Int, getPrevIndex: (Int) -> Int) {
        val baseIndex = pendingTargetIndex ?: currentIndex
        val target = getPrevIndex(baseIndex)
        skipTo(target, maxQueueSize)
    }

    fun cancel() {
        debounceJob?.cancel()
        debounceJob = null
        pendingTargetIndex = null
    }
}
