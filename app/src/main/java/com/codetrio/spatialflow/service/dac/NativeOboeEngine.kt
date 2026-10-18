package com.codetrio.spatialflow.service.dac

import android.util.Log
import java.nio.ByteBuffer

/**
 * Kotlin singleton bridge for SpatialFlow's high-performance native Oboe audio engine.
 *
 * Provides direct zero-copy byte writing into native C++ SPSC ring buffer and
 * manages the lifecycle of the exclusive AAudio MMAP output stream.
 */
object NativeOboeEngine {
    private const val TAG = "NativeOboeEngine"

    private var isLibraryLoaded = false

    var onStreamDisconnected: ((Int) -> Unit)? = null

    init {
        try {
            System.loadLibrary("spatialflow_audio")
            isLibraryLoaded = true
            nativeInit()
            Log.i(TAG, "Successfully loaded native SpatialFlow audio engine (Oboe v1.11.1)")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load spatialflow_audio native library: ${e.message}")
            isLibraryLoaded = false
        }
    }

    val isAvailable: Boolean
        get() = isLibraryLoaded

    /**
     * Opens an exclusive, bit-perfect AAudio MMAP stream on the specified DAC device endpoint.
     */
    fun openStream(deviceId: Int, sampleRate: Int, channels: Int, bitDepth: Int): Boolean {
        if (!isLibraryLoaded) return false
        return try {
            nativeOpenStream(deviceId, sampleRate, channels, bitDepth)
        } catch (e: Exception) {
            Log.e(TAG, "Exception opening native stream: ${e.message}")
            false
        }
    }

    /**
     * Zero-copy write using direct off-heap ByteBuffer address.
     */
    fun writeDirectBuffer(directByteBuffer: ByteBuffer, offsetBytes: Int, lengthBytes: Int): Int {
        if (!isLibraryLoaded || lengthBytes <= 0) return 0
        return try {
            nativeWriteDirectBuffer(directByteBuffer, offsetBytes, lengthBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Exception writing direct buffer: ${e.message}")
            0
        }
    }

    /**
     * Fallback byte array write (copies memory across JNI boundary).
     */
    fun writeByteArray(byteArray: ByteArray, offset: Int, length: Int): Int {
        if (!isLibraryLoaded || length <= 0) return 0
        return try {
            nativeWriteByteArray(byteArray, offset, length)
        } catch (e: Exception) {
            Log.e(TAG, "Exception writing byte array: ${e.message}")
            0
        }
    }

    fun start(): Boolean = if (isLibraryLoaded) nativeStart() else false
    fun pause(): Boolean = if (isLibraryLoaded) nativePause() else false
    fun flush(): Boolean = if (isLibraryLoaded) nativeFlush() else false
    fun stop(): Boolean = if (isLibraryLoaded) nativeStop() else false
    fun close() {
        if (isLibraryLoaded) nativeClose()
    }

    fun hasDrained(): Boolean = if (isLibraryLoaded) nativeHasDrained() else true
    fun getHardwareTimestampUs(): Long = if (isLibraryLoaded) nativeGetHardwareTimestampUs() else 0L

    val sampleRate: Int
        get() = if (isLibraryLoaded) nativeGetSampleRate() else 0

    val actualSampleRate: Int
        get() = if (isLibraryLoaded) nativeGetActualSampleRate() else 0

    val channelCount: Int
        get() = if (isLibraryLoaded) nativeGetChannelCount() else 0

    val bitDepth: Int
        get() = if (isLibraryLoaded) nativeGetBitDepth() else 0

    val isStreamOpen: Boolean
        get() = if (isLibraryLoaded) nativeIsStreamOpen() else false

    val isExclusive: Boolean
        get() = if (isLibraryLoaded) nativeIsExclusive() else false

    val sharingMode: String
        get() = if (isLibraryLoaded) nativeGetSharingMode() else "Closed"

    val audioApi: String
        get() = if (isLibraryLoaded) nativeGetAudioApi() else "None"

    val lastError: String
        get() = if (isLibraryLoaded) nativeGetLastError() else "Unknown"

    val framesPerBurst: Int
        get() = if (isLibraryLoaded) nativeGetFramesPerBurst() else 0

    val bufferSizeInFrames: Int
        get() = if (isLibraryLoaded) nativeGetBufferSizeInFrames() else 0

    val underflowCount: Int
        get() = if (isLibraryLoaded) nativeGetUnderflowCount() else 0

    val totalFramesRendered: Long
        get() = if (isLibraryLoaded) nativeGetTotalFramesRendered() else 0L

    // Called from JNI when hardware disconnect occurs
    @JvmStatic
    @Suppress("unused")
    fun onNativeStreamDisconnected(errorCode: Int) {
        Log.w(TAG, "Native stream disconnected with error code: $errorCode")
        onStreamDisconnected?.invoke(errorCode)
    }

    // JNI Native Declarations
    private external fun nativeInit()
    private external fun nativeOpenStream(deviceId: Int, sampleRate: Int, channels: Int, bitDepth: Int): Boolean
    private external fun nativeWriteDirectBuffer(directByteBuffer: ByteBuffer, offsetBytes: Int, lengthBytes: Int): Int
    private external fun nativeWriteByteArray(byteArray: ByteArray, offset: Int, length: Int): Int
    private external fun nativeStart(): Boolean
    private external fun nativePause(): Boolean
    private external fun nativeFlush(): Boolean
    private external fun nativeStop(): Boolean
    private external fun nativeClose()
    private external fun nativeHasDrained(): Boolean
    private external fun nativeGetHardwareTimestampUs(): Long
    private external fun nativeGetSampleRate(): Int
    private external fun nativeGetActualSampleRate(): Int
    private external fun nativeGetChannelCount(): Int
    private external fun nativeGetBitDepth(): Int
    private external fun nativeIsStreamOpen(): Boolean
    private external fun nativeIsExclusive(): Boolean
    private external fun nativeGetSharingMode(): String
    private external fun nativeGetAudioApi(): String
    private external fun nativeGetLastError(): String
    private external fun nativeGetFramesPerBurst(): Int
    private external fun nativeGetBufferSizeInFrames(): Int
    private external fun nativeGetUnderflowCount(): Int
    private external fun nativeGetTotalFramesRendered(): Long
}
