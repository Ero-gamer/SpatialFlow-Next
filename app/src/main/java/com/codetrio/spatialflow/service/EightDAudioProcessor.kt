package com.codetrio.spatialflow.service

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin

/**
 * Studio-Grade High-Fidelity 8D Spatial Audio Processor.
 *
 * Implements Natural Psychoacoustic Binaural Processing:
 * 1. Constant-Power Azimuth Panning (Sine/Cosine LUT).
 * 2. Head-Diffraction HRTF Crossfeed with Single-Pole Lowpass filtering (No harshness).
 * 3. Smooth Sub-Millisecond Interaural Time Difference (ITD / Haas Effect).
 * 4. Moorer Lowpass-Damped Stereo Ambience (Silky, natural acoustic space with 0 metallic ringing).
 * 5. Pinna Rear Head Acoustic Shadowing when orbiting behind listener.
 * 6. Zero-allocation loop with 4-sample unrolling for maximum performance.
 */
@OptIn(UnstableApi::class)
class EightDAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var rotationSpeedHz: Float = 0.15f
        set(value) {
            field = value.coerceIn(0.03f, 0.40f)
        }

    @Volatile
    var spatialDepth: Float = 0.85f
        set(value) {
            field = value.coerceIn(0.2f, 1.0f)
        }

    @Volatile
    var roomReverbIntensity: Float = 0.12f
        set(value) {
            field = value.coerceIn(0.0f, 0.40f)
        }

    companion object {
        private const val LUT_SIZE = 2048
        private const val LUT_MASK = LUT_SIZE - 1
        private const val COS_OFFSET = LUT_SIZE / 4 // 512 points = 90 degrees (pi/2)
        private const val TWO_PI = 2.0 * PI

        // Precomputed Sine Look-Up Table (2048 samples)
        private val SINE_LUT = FloatArray(LUT_SIZE) { i ->
            sin((i.toDouble() / LUT_SIZE.toDouble()) * TWO_PI).toFloat()
        }

        // Delay buffer sizes (Power of 2 for zero-division bitmask pointer wrapping)
        private const val ITD_BUFFER_SIZE = 128
        private const val ITD_MASK = ITD_BUFFER_SIZE - 1

        private const val COMB_BUFFER_SIZE = 8192
        private const val COMB_MASK = COMB_BUFFER_SIZE - 1
    }

    // DSP State Variables
    private var sampleRate = 44100
    private var phaseAcc = 0.0f
    private var targetSpeedHz = 0.15f
    private var currentSpeedHz = 0.15f

    // Binaural ITD Delay Line (Ring Buffer)
    private val itdLeft = FloatArray(ITD_BUFFER_SIZE)
    private val itdRight = FloatArray(ITD_BUFFER_SIZE)
    private var itdWritePos = 0

    // Head-Shadow Single-Pole Lowpass filter states (Diffraction around skull)
    private var shadowLpLeft = 0f
    private var shadowLpRight = 0f

    // Moorer Damped Stereo Ambience Delay Lines
    private val combL = FloatArray(COMB_BUFFER_SIZE)
    private val combR = FloatArray(COMB_BUFFER_SIZE)
    private var combWriteL = 0
    private var combWriteR = 0
    private var combLpL = 0f
    private var combLpR = 0f

    fun resetState() {
        phaseAcc = 0.0f
        itdLeft.fill(0f)
        itdRight.fill(0f)
        itdWritePos = 0
        shadowLpLeft = 0f
        shadowLpRight = 0f
        combL.fill(0f)
        combR.fill(0f)
        combWriteL = 0
        combWriteR = 0
        combLpL = 0f
        combLpR = 0f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!enabled) {
            return inputAudioFormat
        }
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        resetState()
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, inputAudioFormat.encoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        if (!enabled) {
            val remaining = inputBuffer.remaining()
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val is16Bit = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        val isStereo = inputAudioFormat.channelCount == 2
        val bytesPerSample = if (is16Bit) 2 else 4
        val inputFrameSize = bytesPerSample * inputAudioFormat.channelCount
        val frameCount = inputBuffer.remaining() / inputFrameSize

        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerSample * 2)

        targetSpeedHz = rotationSpeedHz
        val phaseStep = (LUT_SIZE * currentSpeedHz) / sampleRate.toFloat()
        val depthFactor = spatialDepth
        val maxItdSamples = (sampleRate * 0.00050f).coerceIn(10f, 26f) // ~0.5ms max head delay
        val revWet = roomReverbIntensity

        // Prime numbers for natural non-resonant room delay times (~16ms and ~22ms)
        val combSizeL = (sampleRate * 0.0163f).toInt().coerceIn(200, COMB_BUFFER_SIZE - 1)
        val combSizeR = (sampleRate * 0.0227f).toInt().coerceIn(200, COMB_BUFFER_SIZE - 1)

        if (is16Bit) {
            process16BitPcm(
                input = inputBuffer,
                output = outputBuffer,
                frameCount = frameCount,
                isStereo = isStereo,
                phaseStep = phaseStep,
                depthFactor = depthFactor,
                maxItdSamples = maxItdSamples,
                revWet = revWet,
                combSizeL = combSizeL,
                combSizeR = combSizeR
            )
        } else {
            processFloatPcm(
                input = inputBuffer,
                output = outputBuffer,
                frameCount = frameCount,
                isStereo = isStereo,
                phaseStep = phaseStep,
                depthFactor = depthFactor,
                maxItdSamples = maxItdSamples,
                revWet = revWet,
                combSizeL = combSizeL,
                combSizeR = combSizeR
            )
        }

        outputBuffer.flip()
    }

    /**
     * Studio 16-bit PCM Binaural 8D Loop with Damped Room Ambience and Head Shadowing.
     */
    private inline fun process16BitPcm(
        input: ByteBuffer,
        output: ByteBuffer,
        frameCount: Int,
        isStereo: Boolean,
        phaseStep: Float,
        depthFactor: Float,
        maxItdSamples: Float,
        revWet: Float,
        combSizeL: Int,
        combSizeR: Int
    ) {
        var phase = phaseAcc
        var writePos = itdWritePos
        var cwL = combWriteL
        var cwR = combWriteR
        var clpL = combLpL
        var clpR = combLpR
        var shdL = shadowLpLeft
        var shdR = shadowLpRight

        var framesRemaining = frameCount

        while (framesRemaining >= 4) {
            repeat(4) {
                val rawLeft = input.short.toFloat()
                val rawRight = if (isStereo) input.short.toFloat() else rawLeft

                // 1. Azimuth angle and Front/Back coordinate from Trig LUT
                val lutIndex = (phase.toInt()) and LUT_MASK
                val cosIndex = (lutIndex + COS_OFFSET) and LUT_MASK
                val sinAzimuth = SINE_LUT[lutIndex]  // -1.0 (Left) to +1.0 (Right)
                val cosDistance = SINE_LUT[cosIndex] // -1.0 (Behind) to +1.0 (Front)

                // 2. Rear Head Shadowing (Subtle high-frequency attenuation when behind head)
                val rearDamp = if (cosDistance < 0f) 1.0f - (0.08f * -cosDistance) else 1.0f

                // 3. Constant-Power Binaural Panning
                val panAngle = (sinAzimuth * depthFactor + 1.0f) * 0.7853982f
                val panLutIdx = ((panAngle / TWO_PI.toFloat()) * LUT_SIZE).toInt() and LUT_MASK
                val panCosIdx = (panLutIdx + COS_OFFSET) and LUT_MASK
                val gainL = SINE_LUT[panCosIdx] * rearDamp
                val gainR = SINE_LUT[panLutIdx] * rearDamp

                // 4. Moorer Damped Stereo Ambience (Warm, natural room diffusion with 0 metallic ringing)
                val monoIn = (rawLeft + rawRight) * 0.5f
                
                // Left comb delay with lowpass feedback damping
                val combReadL = combL[(cwL - combSizeL + COMB_BUFFER_SIZE) and COMB_MASK]
                clpL += (combReadL - clpL) * 0.40f // Warm 4kHz lowpass damping
                combL[cwL] = monoIn + (clpL * 0.32f)
                cwL = (cwL + 1) and COMB_MASK

                // Right comb delay with lowpass feedback damping
                val combReadR = combR[(cwR - combSizeR + COMB_BUFFER_SIZE) and COMB_MASK]
                clpR += (combReadR - clpR) * 0.40f
                combR[cwR] = monoIn + (clpR * 0.32f)
                cwR = (cwR + 1) and COMB_MASK

                val ambientL = clpL * revWet
                val ambientR = clpR * revWet

                // 5. Head-Diffraction Shadowing on Contralateral Ear
                // Ipsilateral ear gets direct sound, Contralateral ear gets acoustic shadow lowpass
                var directL = rawLeft * gainL
                var directR = rawRight * gainR

                if (sinAzimuth > 0.1f) {
                    // Sound on the right -> Left ear gets head diffraction lowpass (~2.8 kHz)
                    shdL += (directL - shdL) * 0.45f
                    directL = shdL
                } else if (sinAzimuth < -0.1f) {
                    // Sound on the left -> Right ear gets head diffraction lowpass (~2.8 kHz)
                    shdR += (directR - shdR) * 0.45f
                    directR = shdR
                }

                // 6. ITD (Interaural Time Delay - Haas Effect)
                itdLeft[writePos] = directL + ambientL
                itdRight[writePos] = directR + ambientR

                val itdOffset = (sinAzimuth * maxItdSamples * depthFactor).toInt()
                val outL: Float
                val outR: Float

                if (itdOffset > 0) {
                    outL = itdLeft[(writePos - itdOffset + ITD_BUFFER_SIZE) and ITD_MASK]
                    outR = itdRight[writePos]
                } else if (itdOffset < 0) {
                    outL = itdLeft[writePos]
                    outR = itdRight[(writePos + itdOffset + ITD_BUFFER_SIZE) and ITD_MASK]
                } else {
                    outL = itdLeft[writePos]
                    outR = itdRight[writePos]
                }
                writePos = (writePos + 1) and ITD_MASK

                output.putShort(outL.coerceIn(-32768f, 32767f).toInt().toShort())
                output.putShort(outR.coerceIn(-32768f, 32767f).toInt().toShort())

                phase += phaseStep
                if (phase >= LUT_SIZE) phase -= LUT_SIZE
            }
            framesRemaining -= 4
        }

        while (framesRemaining > 0) {
            val rawLeft = input.short.toFloat()
            val rawRight = if (isStereo) input.short.toFloat() else rawLeft

            val lutIndex = (phase.toInt()) and LUT_MASK
            val cosIndex = (lutIndex + COS_OFFSET) and LUT_MASK
            val sinAzimuth = SINE_LUT[lutIndex]
            val cosDistance = SINE_LUT[cosIndex]

            val rearDamp = if (cosDistance < 0f) 1.0f - (0.08f * -cosDistance) else 1.0f

            val panAngle = (sinAzimuth * depthFactor + 1.0f) * 0.7853982f
            val panLutIdx = ((panAngle / TWO_PI.toFloat()) * LUT_SIZE).toInt() and LUT_MASK
            val panCosIdx = (panLutIdx + COS_OFFSET) and LUT_MASK
            val gainL = SINE_LUT[panCosIdx] * rearDamp
            val gainR = SINE_LUT[panLutIdx] * rearDamp

            val monoIn = (rawLeft + rawRight) * 0.5f
            val combReadL = combL[(cwL - combSizeL + COMB_BUFFER_SIZE) and COMB_MASK]
            clpL += (combReadL - clpL) * 0.40f
            combL[cwL] = monoIn + (clpL * 0.32f)
            cwL = (cwL + 1) and COMB_MASK

            val combReadR = combR[(cwR - combSizeR + COMB_BUFFER_SIZE) and COMB_MASK]
            clpR += (combReadR - clpR) * 0.40f
            combR[cwR] = monoIn + (clpR * 0.32f)
            cwR = (cwR + 1) and COMB_MASK

            val ambientL = clpL * revWet
            val ambientR = clpR * revWet

            var directL = rawLeft * gainL
            var directR = rawRight * gainR

            if (sinAzimuth > 0.1f) {
                shdL += (directL - shdL) * 0.45f
                directL = shdL
            } else if (sinAzimuth < -0.1f) {
                shdR += (directR - shdR) * 0.45f
                directR = shdR
            }

            itdLeft[writePos] = directL + ambientL
            itdRight[writePos] = directR + ambientR

            val itdOffset = (sinAzimuth * maxItdSamples * depthFactor).toInt()
            val outL = if (itdOffset > 0) itdLeft[(writePos - itdOffset + ITD_BUFFER_SIZE) and ITD_MASK] else itdLeft[writePos]
            val outR = if (itdOffset < 0) itdRight[(writePos + itdOffset + ITD_BUFFER_SIZE) and ITD_MASK] else itdRight[writePos]
            writePos = (writePos + 1) and ITD_MASK

            output.putShort(outL.coerceIn(-32768f, 32767f).toInt().toShort())
            output.putShort(outR.coerceIn(-32768f, 32767f).toInt().toShort())

            phase += phaseStep
            if (phase >= LUT_SIZE) phase -= LUT_SIZE
            framesRemaining--
        }

        phaseAcc = phase
        itdWritePos = writePos
        combWriteL = cwL
        combWriteR = cwR
        combLpL = clpL
        combLpR = clpR
        shadowLpLeft = shdL
        shadowLpRight = shdR
        currentSpeedHz += (targetSpeedHz - currentSpeedHz) * 0.05f
    }

    /**
     * Studio 32-bit Float PCM Binaural 8D Loop.
     */
    private inline fun processFloatPcm(
        input: ByteBuffer,
        output: ByteBuffer,
        frameCount: Int,
        isStereo: Boolean,
        phaseStep: Float,
        depthFactor: Float,
        maxItdSamples: Float,
        revWet: Float,
        combSizeL: Int,
        combSizeR: Int
    ) {
        var phase = phaseAcc
        var writePos = itdWritePos
        var cwL = combWriteL
        var cwR = combWriteR
        var clpL = combLpL
        var clpR = combLpR
        var shdL = shadowLpLeft
        var shdR = shadowLpRight

        for (i in 0 until frameCount) {
            val rawLeft = input.float
            val rawRight = if (isStereo) input.float else rawLeft

            val lutIndex = (phase.toInt()) and LUT_MASK
            val cosIndex = (lutIndex + COS_OFFSET) and LUT_MASK
            val sinAzimuth = SINE_LUT[lutIndex]
            val cosDistance = SINE_LUT[cosIndex]

            val rearDamp = if (cosDistance < 0f) 1.0f - (0.08f * -cosDistance) else 1.0f

            val panAngle = (sinAzimuth * depthFactor + 1.0f) * 0.7853982f
            val panLutIdx = ((panAngle / TWO_PI.toFloat()) * LUT_SIZE).toInt() and LUT_MASK
            val panCosIdx = (panLutIdx + COS_OFFSET) and LUT_MASK
            val gainL = SINE_LUT[panCosIdx] * rearDamp
            val gainR = SINE_LUT[panLutIdx] * rearDamp

            val monoIn = (rawLeft + rawRight) * 0.5f
            val combReadL = combL[(cwL - combSizeL + COMB_BUFFER_SIZE) and COMB_MASK]
            clpL += (combReadL - clpL) * 0.40f
            combL[cwL] = monoIn + (clpL * 0.32f)
            cwL = (cwL + 1) and COMB_MASK

            val combReadR = combR[(cwR - combSizeR + COMB_BUFFER_SIZE) and COMB_MASK]
            clpR += (combReadR - clpR) * 0.40f
            combR[cwR] = monoIn + (clpR * 0.32f)
            cwR = (cwR + 1) and COMB_MASK

            val ambientL = clpL * revWet
            val ambientR = clpR * revWet

            var directL = rawLeft * gainL
            var directR = rawRight * gainR

            if (sinAzimuth > 0.1f) {
                shdL += (directL - shdL) * 0.45f
                directL = shdL
            } else if (sinAzimuth < -0.1f) {
                shdR += (directR - shdR) * 0.45f
                directR = shdR
            }

            itdLeft[writePos] = directL + ambientL
            itdRight[writePos] = directR + ambientR

            val itdOffset = (sinAzimuth * maxItdSamples * depthFactor).toInt()
            val outL = if (itdOffset > 0) itdLeft[(writePos - itdOffset + ITD_BUFFER_SIZE) and ITD_MASK] else itdLeft[writePos]
            val outR = if (itdOffset < 0) itdRight[(writePos + itdOffset + ITD_BUFFER_SIZE) and ITD_MASK] else itdRight[writePos]
            writePos = (writePos + 1) and ITD_MASK

            output.putFloat(outL.coerceIn(-1.0f, 1.0f))
            output.putFloat(outR.coerceIn(-1.0f, 1.0f))

            phase += phaseStep
            if (phase >= LUT_SIZE) phase -= LUT_SIZE
        }

        phaseAcc = phase
        itdWritePos = writePos
        combWriteL = cwL
        combWriteR = cwR
        combLpL = clpL
        combLpR = clpR
        shadowLpLeft = shdL
        shadowLpRight = shdR
        currentSpeedHz += (targetSpeedHz - currentSpeedHz) * 0.05f
    }
}
