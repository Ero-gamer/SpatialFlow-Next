package com.codetrio.spatialflow.audio

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import com.codetrio.spatialflow.service.EightDAudioProcessor
import com.codetrio.spatialflow.service.dac.AudioTelemetryProcessor
import com.codetrio.spatialflow.service.dac.BitPerfectAudioProcessorChain
import com.codetrio.spatialflow.service.dac.BitPerfectRuntimeState
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import com.codetrio.spatialflow.service.dac.BitPerfectResult
import com.codetrio.spatialflow.service.dac.MixerAttribute
import com.codetrio.spatialflow.service.CrossfadeGuard
import com.codetrio.spatialflow.service.dac.MixerAttributesProvider
import com.codetrio.spatialflow.service.dac.UsbDacManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BitPerfectPipelineTest {

    @Test
    fun testBitPerfectActiveReturnsEmptyAudioProcessors() {
        val telemetry = AudioTelemetryProcessor(observeOnly = true)
        val dsp1 = EightDAudioProcessor()
        val chain = BitPerfectAudioProcessorChain(
            telemetryProcessor = telemetry,
            dspProcessors = arrayOf(dsp1),
            isBitPerfectProvider = { true }
        )
        val processors = chain.audioProcessors
        assertEquals("Bit-perfect active => getAudioProcessors() must be empty", 0, processors.size)
    }

    @Test
    fun testBitPerfectActiveReturnsDefaultPlaybackParameters() {
        val telemetry = AudioTelemetryProcessor(observeOnly = true)
        val dsp1 = EightDAudioProcessor()
        val chain = BitPerfectAudioProcessorChain(
            telemetryProcessor = telemetry,
            dspProcessors = arrayOf(dsp1),
            isBitPerfectProvider = { true }
        )
        val customParams = PlaybackParameters(1.5f, 1.25f)
        val applied = chain.applyPlaybackParameters(customParams)
        assertEquals("Bit-perfect active => applyPlaybackParameters must return DEFAULT", PlaybackParameters.DEFAULT, applied)
    }

    @Test
    fun testBitPerfectActiveReturnsFalseForSkipSilence() {
        val telemetry = AudioTelemetryProcessor(observeOnly = true)
        val dsp1 = EightDAudioProcessor()
        val chain = BitPerfectAudioProcessorChain(
            telemetryProcessor = telemetry,
            dspProcessors = arrayOf(dsp1),
            isBitPerfectProvider = { true }
        )
        val skipSilence = chain.applySkipSilenceEnabled(true)
        assertFalse("Bit-perfect active => applySkipSilenceEnabled must return false", skipSilence)
    }

    @Test
    fun testStudioModeIncludesDspProcessorsInOrder() {
        val telemetry = AudioTelemetryProcessor(observeOnly = true)
        val dsp1 = EightDAudioProcessor()
        val dsp2 = EightDAudioProcessor()
        val chain = BitPerfectAudioProcessorChain(
            telemetryProcessor = telemetry,
            dspProcessors = arrayOf(dsp1, dsp2),
            isBitPerfectProvider = { false }
        )
        val processors = chain.audioProcessors
        assertEquals("Studio mode must include telemetry plus all DSP processors", 3, processors.size)
        assertEquals("First processor must be telemetry", telemetry, processors[0])
        assertEquals("Second processor must be dsp1", dsp1, processors[1])
        assertEquals("Third processor must be dsp2", dsp2, processors[2])
    }

    @Test
    fun testBitPerfectChainBypassesAllSampleModifyingDsp() {
        val telemetryProcessor = AudioTelemetryProcessor(observeOnly = true)
        val eightDProcessor = EightDAudioProcessor()
        var bitPerfectEnabled = true

        val chain = BitPerfectAudioProcessorChain(
            telemetryProcessor = telemetryProcessor,
            dspProcessors = arrayOf(eightDProcessor),
            isBitPerfectProvider = { bitPerfectEnabled }
        )

        // 1. Bit-Perfect Active: Empty processor array
        bitPerfectEnabled = true
        val bitPerfectProcessors = chain.audioProcessors
        assertEquals("Bit-Perfect chain must be empty", 0, bitPerfectProcessors.size)

        // Verify speed/pitch modification is bypassed in bit-perfect mode
        val params = PlaybackParameters(1.5f, 1.2f)
        val appliedParams = chain.applyPlaybackParameters(params)
        assertEquals("Bit-Perfect mode must return unaltered original speed", 1.0f, appliedParams.speed, 0.001f)
        assertEquals("Bit-Perfect mode must return unaltered original pitch", 1.0f, appliedParams.pitch, 0.001f)

        // Verify silence skipping is disabled in bit-perfect mode
        val skipSilence = chain.applySkipSilenceEnabled(true)
        assertFalse("Bit-Perfect mode must never enable silence skipping DSP", skipSilence)

        // 2. Normal DSP Active: All processors restored
        bitPerfectEnabled = false
        val dspProcessors = chain.audioProcessors
        assertTrue("Normal DSP chain must include all effect processors", dspProcessors.size > 1)
        assertTrue("Must include EightDAudioProcessor in DSP chain", dspProcessors.contains(eightDProcessor))
        assertTrue("Must include AudioTelemetryProcessor in DSP chain", dspProcessors.contains(telemetryProcessor))
    }

    @Test
    fun testAudioTelemetryProcessorPreservesPcm24AndPcm32WithoutConversion() {
        val processor = AudioTelemetryProcessor(observeOnly = true)

        // 24-bit Packed PCM @ 96 kHz
        val pcm24Format = AudioProcessor.AudioFormat(96000, 2, C.ENCODING_PCM_24BIT)
        val outputFormat24 = processor.configure(pcm24Format)
        assertEquals("Sample rate 96kHz must be preserved exactly", 96000, outputFormat24.sampleRate)
        assertEquals("Channel count must be preserved exactly", 2, outputFormat24.channelCount)
        assertEquals("PCM24 must NOT be converted to PCM16 or Float", C.ENCODING_PCM_24BIT, outputFormat24.encoding)

        // 32-bit Integer PCM @ 192 kHz
        val pcm32Format = AudioProcessor.AudioFormat(192000, 2, C.ENCODING_PCM_32BIT)
        val outputFormat32 = processor.configure(pcm32Format)
        assertEquals("Sample rate 192kHz must be preserved exactly", 192000, outputFormat32.sampleRate)
        assertEquals("Channel count must be preserved exactly", 2, outputFormat32.channelCount)
        assertEquals("PCM32 must NOT be collapsed to PCM16", C.ENCODING_PCM_32BIT, outputFormat32.encoding)

        // 16-bit PCM @ 44.1 kHz
        val pcm16Format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val outputFormat16 = processor.configure(pcm16Format)
        assertEquals(44100, outputFormat16.sampleRate)
        assertEquals(C.ENCODING_PCM_16BIT, outputFormat16.encoding)
    }

    @Test
    fun testAudioTelemetryPassesAudioBuffersThroughUntouched() {
        val processor = AudioTelemetryProcessor(observeOnly = true)
        val format = AudioProcessor.AudioFormat(96000, 2, C.ENCODING_PCM_24BIT)
        processor.configure(format)
        processor.flush()

        // Create sample PCM data
        val inputData = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
        val inputBuffer = ByteBuffer.allocateDirect(inputData.size).order(ByteOrder.nativeOrder())
        inputBuffer.put(inputData)
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val outputBuffer = processor.output

        val outputBytes = ByteArray(outputBuffer.remaining())
        outputBuffer.get(outputBytes)

        assertEquals("Output buffer length must match input byte-for-byte", inputData.size, outputBytes.size)
        for (i in inputData.indices) {
            assertEquals("Byte $i must be bit-identical", inputData[i], outputBytes[i])
        }
    }

    @Test
    fun testEightDProcessorTransparentWhenDisabled() {
        val eightD = EightDAudioProcessor()
        eightD.enabled = false

        // Should accept PCM24 without rejecting format
        val pcm24Format = AudioProcessor.AudioFormat(96000, 2, C.ENCODING_PCM_24BIT)
        val configured = eightD.configure(pcm24Format)
        assertEquals(C.ENCODING_PCM_24BIT, configured.encoding)
        assertEquals(96000, configured.sampleRate)
        assertEquals(2, configured.channelCount)

        eightD.flush()

        val testBytes = byteArrayOf(0x10, 0x20, 0x30, 0x40, 0x50, 0x60)
        val buffer = ByteBuffer.allocateDirect(testBytes.size).order(ByteOrder.nativeOrder())
        buffer.put(testBytes)
        buffer.flip()

        eightD.queueInput(buffer)
        val output = eightD.output
        val outBytes = ByteArray(output.remaining())
        output.get(outBytes)

        assertEquals("When disabled, EightD must pass data untouched", testBytes.size, outBytes.size)
        for (i in testBytes.indices) {
            assertEquals("Sample byte $i must not be modified", testBytes[i], outBytes[i])
        }
    }

    @Test
    fun testBitPerfectRuntimeStateDisplayFormatting() {
        // Case 1: Verified Bit-Perfect 24-bit 96kHz
        val stateVerified = BitPerfectRuntimeState(
            sourceEncoding = "24-bit Packed PCM",
            sourceBitDepth = 24,
            sourceSampleRate = 96000,
            outputEncoding = "24-bit Packed PCM",
            outputBitDepth = 24,
            outputSampleRate = 96000,
            channels = 2,
            directPlaybackSupported = true,
            nativeRateMatched = true,
            resamplerActive = false,
            dspActive = false,
            softwareVolumeActive = false,
            usbExclusiveActive = false,
            mixerBitPerfectActive = true,
            verifiedBitPerfect = true,
            failureReason = null
        )
        assertEquals("Bit-Perfect • 24-bit • 96 kHz", stateVerified.liveStatusDisplay)
        assertTrue(stateVerified.verifiedBitPerfect)
        assertFalse(stateVerified.dspActive)
        assertFalse(stateVerified.resamplerActive)

        // Case 2: Native Rate 24-bit 44.1kHz (direct native rate matching without Android 14 bit-perfect mixer)
        val stateNativeRate = BitPerfectRuntimeState(
            sourceEncoding = "24-bit Packed PCM",
            sourceBitDepth = 24,
            sourceSampleRate = 44100,
            outputEncoding = "24-bit Packed PCM",
            outputBitDepth = 24,
            outputSampleRate = 44100,
            channels = 2,
            directPlaybackSupported = true,
            nativeRateMatched = true,
            resamplerActive = false,
            dspActive = false,
            softwareVolumeActive = false,
            usbExclusiveActive = false,
            mixerBitPerfectActive = false,
            verifiedBitPerfect = false,
            failureReason = null
        )
        assertEquals("Native Rate • 24-bit • 44.1 kHz", stateNativeRate.liveStatusDisplay)

        // Case 3: Unsupported DAC native rate -> Resampling occurs (never silently claim bit-perfect!)
        val stateResampling = BitPerfectRuntimeState(
            sourceEncoding = "24-bit Packed PCM",
            sourceBitDepth = 24,
            sourceSampleRate = 96000,
            outputEncoding = "24-bit Packed PCM",
            outputBitDepth = 24,
            outputSampleRate = 48000,
            channels = 2,
            directPlaybackSupported = false,
            nativeRateMatched = false,
            resamplerActive = true,
            dspActive = false,
            softwareVolumeActive = false,
            usbExclusiveActive = false,
            mixerBitPerfectActive = false,
            verifiedBitPerfect = false,
            failureReason = "Native rate 96.0 kHz not supported by DAC"
        )
        assertEquals("Resampling • 24-bit/96 kHz → 48 kHz", stateResampling.liveStatusDisplay)
        assertTrue(stateResampling.resamplerActive)
        assertFalse(stateResampling.verifiedBitPerfect)

        // Case 4: No USB DAC connected
        val stateUnavailable = BitPerfectRuntimeState(
            sourceEncoding = "16-bit PCM",
            sourceBitDepth = 16,
            sourceSampleRate = 44100,
            outputEncoding = "16-bit PCM",
            outputBitDepth = 16,
            outputSampleRate = 48000,
            channels = 2,
            directPlaybackSupported = false,
            nativeRateMatched = false,
            resamplerActive = false,
            dspActive = true,
            softwareVolumeActive = false,
            usbExclusiveActive = false,
            mixerBitPerfectActive = false,
            verifiedBitPerfect = false,
            failureReason = "Bit-Perfect unavailable: USB DAC not connected"
        )
        assertEquals("Bit-Perfect unavailable", stateUnavailable.liveStatusDisplay)
    }

    // ── MixerAttributesProvider and BitPerfectResult branch tests ──────────

    private class FakeMixerAttributesProvider(
        var supportedList: List<MixerAttribute> = emptyList(),
        var setReturnValue: Boolean = true,
        var readbackAttribute: MixerAttribute? = null,
        var autoUpdateReadbackOnSet: Boolean = false
    ) : MixerAttributesProvider {
        var lastSetAttribute: MixerAttribute? = null
        var clearCalled: Boolean = false

        override fun getSupported(device: AudioDeviceInfo?): List<MixerAttribute> = supportedList

        override fun getPreferred(attributes: AudioAttributes?, device: AudioDeviceInfo?): MixerAttribute? = readbackAttribute

        override fun set(attributes: AudioAttributes?, device: AudioDeviceInfo?, mixerAttribute: MixerAttribute): Boolean {
            lastSetAttribute = mixerAttribute
            if (autoUpdateReadbackOnSet && setReturnValue) {
                readbackAttribute = mixerAttribute
            }
            return setReturnValue
        }

        override fun clear(attributes: AudioAttributes?, device: AudioDeviceInfo?): Boolean {
            clearCalled = true
            readbackAttribute = null
            return true
        }
    }

    @Test
    fun testApplyBitPerfectMixer_belowApi34_returnsFallbackRequiresAndroid14() {
        val fakeProvider = FakeMixerAttributesProvider()
        val result = UsbDacManager.applyBitPerfectMixer(
            device = null,
            sampleRateHz = 96000,
            bitDepth = 24,
            apiLevel = 33,
            provider = fakeProvider
        )
        assertTrue("Below API 34 must return Fallback", result is BitPerfectResult.Fallback)
        assertEquals(
            "Requires Android 14 (API 34) for HAL bit-perfect mixer",
            (result as BitPerfectResult.Fallback).reason
        )
    }

    @Test
    fun testApplyBitPerfectMixer_noBitPerfectAttributes_returnsUnavailable() {
        val fakeProvider = FakeMixerAttributesProvider(
            supportedList = listOf(
                MixerAttribute(
                    mixerBehavior = MixerAttribute.BEHAVIOR_DEFAULT,
                    sampleRate = 96000,
                    encoding = AudioFormat.ENCODING_PCM_24BIT_PACKED
                )
            )
        )
        val result = UsbDacManager.applyBitPerfectMixer(
            device = null,
            sampleRateHz = 96000,
            bitDepth = 24,
            apiLevel = 34,
            provider = fakeProvider
        )
        assertEquals(BitPerfectResult.Unavailable, result)
    }

    @Test
    fun testApplyBitPerfectMixer_rateOrBitDepthMismatch_returnsFallbackDacDoesNotList() {
        val fakeProvider = FakeMixerAttributesProvider(
            supportedList = listOf(
                MixerAttribute(
                    mixerBehavior = MixerAttribute.BEHAVIOR_BIT_PERFECT,
                    sampleRate = 48000,
                    encoding = AudioFormat.ENCODING_PCM_16BIT
                )
            )
        )
        val result = UsbDacManager.applyBitPerfectMixer(
            device = null,
            sampleRateHz = 96000,
            bitDepth = 24,
            apiLevel = 34,
            provider = fakeProvider
        )
        assertTrue(result is BitPerfectResult.Fallback)
        assertEquals(
            "DAC does not list 96000 Hz / 24-bit in bit-perfect mode",
            (result as BitPerfectResult.Fallback).reason
        )
        assertTrue("Fallback must clear preferred mixer attributes", fakeProvider.clearCalled)
    }

    @Test
    fun testApplyBitPerfectMixer_halRejectsAttributes_returnsFallbackHalRejected() {
        val fakeProvider = FakeMixerAttributesProvider(
            supportedList = listOf(
                MixerAttribute(
                    mixerBehavior = MixerAttribute.BEHAVIOR_BIT_PERFECT,
                    sampleRate = 96000,
                    encoding = AudioFormat.ENCODING_PCM_24BIT_PACKED
                )
            ),
            setReturnValue = false
        )
        val result = UsbDacManager.applyBitPerfectMixer(
            device = null,
            sampleRateHz = 96000,
            bitDepth = 24,
            apiLevel = 34,
            provider = fakeProvider
        )
        assertTrue(result is BitPerfectResult.Fallback)
        assertEquals(
            "HAL rejected mixer attributes",
            (result as BitPerfectResult.Fallback).reason
        )
    }

    @Test
    fun testApplyBitPerfectMixer_readbackMismatch_returnsFallbackReadbackDidNotConfirm() {
        // Subcase A: Readback returns null
        val fakeProviderNull = FakeMixerAttributesProvider(
            supportedList = listOf(
                MixerAttribute(
                    mixerBehavior = MixerAttribute.BEHAVIOR_BIT_PERFECT,
                    sampleRate = 96000,
                    encoding = AudioFormat.ENCODING_PCM_24BIT_PACKED
                )
            ),
            setReturnValue = true,
            readbackAttribute = null
        )
        val resultNull = UsbDacManager.applyBitPerfectMixer(
            device = null,
            sampleRateHz = 96000,
            bitDepth = 24,
            apiLevel = 34,
            provider = fakeProviderNull
        )
        assertTrue(resultNull is BitPerfectResult.Fallback)
        assertEquals(
            "Readback did not confirm bit-perfect",
            (resultNull as BitPerfectResult.Fallback).reason
        )

        // Subcase B: Readback returns non-bit-perfect mixer behavior
        val fakeProviderNonBitPerfect = FakeMixerAttributesProvider(
            supportedList = listOf(
                MixerAttribute(
                    mixerBehavior = MixerAttribute.BEHAVIOR_BIT_PERFECT,
                    sampleRate = 96000,
                    encoding = AudioFormat.ENCODING_PCM_24BIT_PACKED
                )
            ),
            setReturnValue = true,
            readbackAttribute = MixerAttribute(
                mixerBehavior = MixerAttribute.BEHAVIOR_DEFAULT,
                sampleRate = 96000,
                encoding = AudioFormat.ENCODING_PCM_24BIT_PACKED
            )
        )
        val resultNonBp = UsbDacManager.applyBitPerfectMixer(
            device = null,
            sampleRateHz = 96000,
            bitDepth = 24,
            apiLevel = 34,
            provider = fakeProviderNonBitPerfect
        )
        assertTrue(resultNonBp is BitPerfectResult.Fallback)
        assertEquals(
            "Readback did not confirm bit-perfect",
            (resultNonBp as BitPerfectResult.Fallback).reason
        )

        // Subcase C: Readback returns different sample rate (e.g. 48000 instead of 96000)
        val fakeProviderWrongRate = FakeMixerAttributesProvider(
            supportedList = listOf(
                MixerAttribute(
                    mixerBehavior = MixerAttribute.BEHAVIOR_BIT_PERFECT,
                    sampleRate = 96000,
                    encoding = AudioFormat.ENCODING_PCM_24BIT_PACKED
                )
            ),
            setReturnValue = true,
            readbackAttribute = MixerAttribute(
                mixerBehavior = MixerAttribute.BEHAVIOR_BIT_PERFECT,
                sampleRate = 48000,
                encoding = AudioFormat.ENCODING_PCM_24BIT_PACKED
            )
        )
        val resultWrongRate = UsbDacManager.applyBitPerfectMixer(
            device = null,
            sampleRateHz = 96000,
            bitDepth = 24,
            apiLevel = 34,
            provider = fakeProviderWrongRate
        )
        assertTrue(resultWrongRate is BitPerfectResult.Fallback)
        assertEquals(
            "Readback did not confirm bit-perfect",
            (resultWrongRate as BitPerfectResult.Fallback).reason
        )
    }

    @Test
    fun testApplyBitPerfectMixer_readbackConfirmed_returnsVerified() {
        val fakeProvider = FakeMixerAttributesProvider(
            supportedList = listOf(
                MixerAttribute(
                    mixerBehavior = MixerAttribute.BEHAVIOR_BIT_PERFECT,
                    sampleRate = 96000,
                    encoding = AudioFormat.ENCODING_PCM_24BIT_PACKED
                )
            ),
            setReturnValue = true,
            autoUpdateReadbackOnSet = true
        )
        val result = UsbDacManager.applyBitPerfectMixer(
            device = null,
            sampleRateHz = 96000,
            bitDepth = 24,
            apiLevel = 34,
            provider = fakeProvider
        )
        assertTrue("Confirmed HAL readback must return Verified", result is BitPerfectResult.Verified)
        val verified = result as BitPerfectResult.Verified
        assertEquals(96000, verified.sampleRateHz)
        assertEquals(24, verified.bitDepth)
    }

    @Test
    fun testBitPerfectRuntimeState_api29To33FallbackDoesNotSetVerified() {
        val fallbackResult = BitPerfectResult.Fallback("Direct route, not verified on this Android version")
        val state = BitPerfectRuntimeState(
            sourceEncoding = "24-bit Packed PCM",
            sourceBitDepth = 24,
            sourceSampleRate = 44100,
            outputEncoding = "24-bit Packed PCM",
            outputBitDepth = 24,
            outputSampleRate = 44100,
            channels = 2,
            directPlaybackSupported = true,
            nativeRateMatched = true,
            resamplerActive = false,
            dspActive = false,
            verifiedBitPerfect = false,
            failureReason = fallbackResult.reason,
            bitPerfectResult = fallbackResult
        )
        assertFalse("API 29-33 must never report verifiedBitPerfect = true", state.verifiedBitPerfect)
        assertEquals("Direct route, not verified on this Android version", state.failureReason)
        assertEquals(fallbackResult, state.bitPerfectResult)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testAudioDeviceCallbackDebounce_nRapidEventsProduceOneInspectCall() = runTest {
        val scheduler = testScheduler
        val testDispatcher = StandardTestDispatcher(scheduler)
        var inspectCallCount = 0

        val originalScope = UsbDacManager.deviceEventScope
        val originalDispatcher = UsbDacManager.deviceEventDispatcher
        UsbDacManager.deviceEventScope = this
        UsbDacManager.deviceEventDispatcher = testDispatcher

        try {
            // Rapidly trigger 5 device callback events 50ms apart
            repeat(5) {
                UsbDacManager.onAudioDeviceChangedDebounced(
                    delayMs = 300L,
                    onInspect = { inspectCallCount++ }
                )
                scheduler.advanceTimeBy(50)
                scheduler.runCurrent()
            }

            // After 200ms elapsed since first event (only 50ms since last event), no inspect call should have executed
            assertEquals("Inspect must not be called before 300ms debounce window elapses", 0, inspectCallCount)

            // Advance past the 300ms debounce window from the final event
            scheduler.advanceTimeBy(300)
            scheduler.runCurrent()

            assertEquals("N rapid events must produce exactly 1 inspect call after 300ms", 1, inspectCallCount)
        } finally {
            UsbDacManager.deviceEventScope = originalScope
            UsbDacManager.deviceEventDispatcher = originalDispatcher
        }
    }

    @Test
    fun testClearMixerConfig_clearsLastAppliedCache() {
        UsbDacManager.lastAppliedAttributes = Triple(96000, AudioFormat.ENCODING_PCM_24BIT_PACKED, 2)
        UsbDacManager.lastAppliedDeviceId = 42

        UsbDacManager.clearMixerConfig()

        assertNull("clearMixerConfig must clear lastAppliedAttributes", UsbDacManager.lastAppliedAttributes)
        assertNull("clearMixerConfig must clear lastAppliedDeviceId", UsbDacManager.lastAppliedDeviceId)
    }

    private class FakeSharedPreferences : android.content.SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? Set<*>)?.map { it.toString() }?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): android.content.SharedPreferences.Editor = FakeEditor()
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

        inner class FakeEditor : android.content.SharedPreferences.Editor {
            private val tempMap = mutableMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor { tempMap[key!!] = value; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor { tempMap[key!!] = values; return this }
            override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor { tempMap[key!!] = value; return this }
            override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor { tempMap[key!!] = value; return this }
            override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor { tempMap[key!!] = value; return this }
            override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor { tempMap[key!!] = value; return this }
            override fun remove(key: String?): android.content.SharedPreferences.Editor { tempMap[key!!] = null; return this }
            override fun clear(): android.content.SharedPreferences.Editor { clear = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clear) map.clear()
                tempMap.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            }
        }
    }

    @Test
    fun testFixedLineOut_appliesMaxVolumeAndPersistsCurrentVolume() {
        val prefs = FakeSharedPreferences()
        var currentStreamVol = 7
        val maxStreamVol = 15
        var playerVolSetToMax = false

        val manager = com.codetrio.spatialflow.service.dac.FixedLineOutVolumeManager(
            prefs = prefs,
            setStreamVolume = { _, index, _ -> currentStreamVol = index },
            getStreamVolume = { currentStreamVol },
            getStreamMaxVolume = { maxStreamVol }
        )

        manager.applyFixedVolume { playerVolSetToMax = true }

        assertTrue("Player volume callback must be invoked", playerVolSetToMax)
        assertEquals("System stream volume must be set to max", maxStreamVol, currentStreamVol)
        assertEquals("Pre-line-out volume must be persisted in prefs", 7, prefs.getInt(com.codetrio.spatialflow.service.dac.FixedLineOutVolumeManager.PREF_KEY_PRE_LINE_OUT_VOLUME, -1))
    }

    @Test
    fun testFixedLineOut_restoreSavedVolume_restoresSystemVolumeAndClearsKey() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putInt(com.codetrio.spatialflow.service.dac.FixedLineOutVolumeManager.PREF_KEY_PRE_LINE_OUT_VOLUME, 8).apply()
        var currentStreamVol = 15

        val manager = com.codetrio.spatialflow.service.dac.FixedLineOutVolumeManager(
            prefs = prefs,
            setStreamVolume = { _, index, _ -> currentStreamVol = index },
            getStreamVolume = { currentStreamVol },
            getStreamMaxVolume = { 15 }
        )

        val restored = manager.restoreSavedVolume()

        assertTrue("restoreSavedVolume must return true when volume was saved", restored)
        assertEquals("System volume must be restored to saved value", 8, currentStreamVol)
        assertEquals("Key in preferences must be reset to -1", -1, prefs.getInt(com.codetrio.spatialflow.service.dac.FixedLineOutVolumeManager.PREF_KEY_PRE_LINE_OUT_VOLUME, -1))
    }

    @Test
    fun testFixedLineOut_restoreAtStartup_restoresOnlyWhenDacNotConnected() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putInt(com.codetrio.spatialflow.service.dac.FixedLineOutVolumeManager.PREF_KEY_PRE_LINE_OUT_VOLUME, 6).apply()
        var currentStreamVol = 15

        val manager = com.codetrio.spatialflow.service.dac.FixedLineOutVolumeManager(
            prefs = prefs,
            setStreamVolume = { _, index, _ -> currentStreamVol = index },
            getStreamVolume = { currentStreamVol },
            getStreamMaxVolume = { 15 }
        )

        // Case A: Startup with DAC connected -> should NOT restore yet
        val restoredWithDac = manager.restoreAtStartup(isDacConnected = true)
        assertFalse("Must not restore volume at startup if DAC is connected", restoredWithDac)
        assertEquals("System volume should remain at max", 15, currentStreamVol)
        assertEquals("Pref key should remain intact", 6, prefs.getInt(com.codetrio.spatialflow.service.dac.FixedLineOutVolumeManager.PREF_KEY_PRE_LINE_OUT_VOLUME, -1))

        // Case B: Startup with DAC disconnected -> MUST restore
        val restoredWithoutDac = manager.restoreAtStartup(isDacConnected = false)
        assertTrue("Must restore volume at startup if DAC is disconnected", restoredWithoutDac)
        assertEquals("System volume must be restored to 6", 6, currentStreamVol)
        assertEquals("Pref key must be reset to -1", -1, prefs.getInt(com.codetrio.spatialflow.service.dac.FixedLineOutVolumeManager.PREF_KEY_PRE_LINE_OUT_VOLUME, -1))
    }

    @Test
    fun testFixedLineOut_doesNotOverwriteExistingSavedVolume() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putInt(com.codetrio.spatialflow.service.dac.FixedLineOutVolumeManager.PREF_KEY_PRE_LINE_OUT_VOLUME, 5).apply()
        var currentStreamVol = 15

        val manager = com.codetrio.spatialflow.service.dac.FixedLineOutVolumeManager(
            prefs = prefs,
            setStreamVolume = { _, index, _ -> currentStreamVol = index },
            getStreamVolume = { currentStreamVol },
            getStreamMaxVolume = { 15 }
        )

        manager.applyFixedVolume()

        assertEquals("Subsequent applyFixedVolume calls must not overwrite original saved volume", 5, prefs.getInt(com.codetrio.spatialflow.service.dac.FixedLineOutVolumeManager.PREF_KEY_PRE_LINE_OUT_VOLUME, -1))
    }

    @Test
    fun testCrossfadeDurationComputation_whenBitPerfectActive_returnsZero() {
        val modeProvider = { true }
        val durationAuto = CrossfadeGuard.computeCrossfadeDurationMs(
            crossfadeEnabled = true,
            crossfadeMode = "auto",
            configuredDurationSec = 5f,
            isBitPerfect = modeProvider()
        )
        val durationCustom = CrossfadeGuard.computeCrossfadeDurationMs(
            crossfadeEnabled = true,
            crossfadeMode = "custom",
            configuredDurationSec = 8f,
            isBitPerfect = modeProvider()
        )

        assertEquals("Bit-perfect active => auto crossfade duration must be 0", 0, durationAuto)
        assertEquals("Bit-perfect active => custom crossfade duration must be 0", 0, durationCustom)
    }

    @Test
    fun testCrossfadeDurationComputation_whenBitPerfectInactive_returnsConfigured() {
        val modeProvider = { false }
        val durationAuto = CrossfadeGuard.computeCrossfadeDurationMs(
            crossfadeEnabled = true,
            crossfadeMode = "auto",
            configuredDurationSec = 5f,
            isBitPerfect = modeProvider()
        )
        val durationCustom = CrossfadeGuard.computeCrossfadeDurationMs(
            crossfadeEnabled = true,
            crossfadeMode = "custom",
            configuredDurationSec = 7f,
            isBitPerfect = modeProvider()
        )
        val durationDisabled = CrossfadeGuard.computeCrossfadeDurationMs(
            crossfadeEnabled = false,
            crossfadeMode = "auto",
            configuredDurationSec = 4f,
            isBitPerfect = modeProvider()
        )

        assertEquals("Bit-perfect inactive => auto mode returns 4000ms default", 4000, durationAuto)
        assertEquals("Bit-perfect inactive => custom mode returns 7000ms", 7000, durationCustom)
        assertEquals("Disabled crossfade returns 0ms", 0, durationDisabled)
    }

    @Test
    fun testStartCrossfadeGuard_whenBitPerfectActive_bypassesAndInvokesBypassCallback() {
        var modeProviderBitPerfect = true
        var bypassLogged = false

        val canExecute = CrossfadeGuard.shouldExecuteCrossfade(
            crossfadeDurationMs = 4000,
            isBitPerfectProvider = { modeProviderBitPerfect },
            onBypassed = { bypassLogged = true }
        )

        assertFalse("Crossfade execution must be disallowed when bit-perfect is active", canExecute)
        assertTrue("Bypass callback must be invoked when bit-perfect is active", bypassLogged)
    }

    @Test
    fun testStartCrossfadeGuard_whenBitPerfectInactive_allowsExecutionWhenDurationPositive() {
        val modeProviderBitPerfect = false
        var bypassLogged = false

        val canExecute = CrossfadeGuard.shouldExecuteCrossfade(
            crossfadeDurationMs = 4000,
            isBitPerfectProvider = { modeProviderBitPerfect },
            onBypassed = { bypassLogged = true }
        )
        val cannotExecuteZeroDuration = CrossfadeGuard.shouldExecuteCrossfade(
            crossfadeDurationMs = 0,
            isBitPerfectProvider = { modeProviderBitPerfect },
            onBypassed = { bypassLogged = true }
        )

        assertTrue("Crossfade execution must be allowed when bit-perfect is inactive and duration > 0", canExecute)
        assertFalse("Crossfade execution must be disallowed when duration is 0", cannotExecuteZeroDuration)
        assertFalse("Bypass callback must NOT be invoked when bit-perfect is inactive", bypassLogged)
    }
}


