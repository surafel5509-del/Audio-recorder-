package com.example.audio

import java.io.File
import kotlin.math.*

enum class VoiceEffectPreset {
    NORMAL,
    DEEP_VOICE,
    CHIPMUNK,
    ROBOT,
    STUDIO_VOCAL,
    RADIO_MEGAPHONE,
    ECHO_CHAMBER
}

data class EqPreset(
    val name: String,
    val band1: Float, // 100 Hz
    val band2: Float, // 400 Hz
    val band3: Float, // 1 kHz
    val band4: Float, // 4 kHz
    val band5: Float  // 12 kHz
)

object AudioEffectsProcessor {

    val EQ_PRESETS = listOf(
        EqPreset("Flat", 0f, 0f, 0f, 0f, 0f),
        EqPreset("Vocal Booster", -2f, 1f, 3f, 5f, 2f),
        EqPreset("Bass Boost", 8f, 4f, 0f, -1f, -2f),
        EqPreset("Treble Boost", -2f, -1f, 1f, 5f, 8f),
        EqPreset("Podcast Crisp", -3f, 0f, 2f, 6f, 3f),
        EqPreset("Acoustic Warmth", 3f, 4f, 2f, 1f, 2f),
        EqPreset("Radio / Megaphone", -10f, 3f, 8f, -2f, -12f)
    )

    /**
     * Applies a voice changer preset to the raw short samples.
     */
    fun applyVoiceEffect(
        samples: ShortArray,
        sampleRate: Int,
        preset: VoiceEffectPreset
    ): ShortArray {
        return when (preset) {
            VoiceEffectPreset.NORMAL -> samples.copyOf()
            VoiceEffectPreset.DEEP_VOICE -> pitchShift(samples, 0.75f)
            VoiceEffectPreset.CHIPMUNK -> pitchShift(samples, 1.4f)
            VoiceEffectPreset.ROBOT -> applyRobotModulation(samples, sampleRate)
            VoiceEffectPreset.STUDIO_VOCAL -> {
                val enhanced = applyVocalEnhancer(samples)
                applyCompressor(enhanced, thresholdDb = -18f, ratio = 3f, makeupGain = 1.3f)
            }
            VoiceEffectPreset.RADIO_MEGAPHONE -> applyRadioBandpass(samples, sampleRate)
            VoiceEffectPreset.ECHO_CHAMBER -> applyEcho(samples, sampleRate, delayMs = 280, feedback = 0.45f, wet = 0.5f)
        }
    }

    /**
     * Pitch shift via linear interpolation resampling with crossfading windowing.
     */
    fun pitchShift(samples: ShortArray, factor: Float): ShortArray {
        if (samples.isEmpty() || abs(factor - 1.0f) < 0.01f) return samples.copyOf()

        val newLength = (samples.size / factor).toInt()
        val output = ShortArray(newLength)

        for (i in 0 until newLength) {
            val originalIndex = i * factor
            val index0 = originalIndex.toInt().coerceIn(0, samples.size - 1)
            val index1 = (index0 + 1).coerceIn(0, samples.size - 1)
            val fraction = originalIndex - index0

            val sample0 = samples[index0].toFloat()
            val sample1 = samples[index1].toFloat()
            val interpolated = sample0 + fraction * (sample1 - sample0)

            output[i] = interpolated.toInt().coerceIn(-32768, 32767).toShort()
        }

        return output
    }

    /**
     * Robot ring modulation effect (multiplies by carrier sine wave).
     */
    fun applyRobotModulation(samples: ShortArray, sampleRate: Int, carrierFreq: Float = 55f): ShortArray {
        val output = ShortArray(samples.size)
        val twoPi = 2.0 * PI
        val step = twoPi * carrierFreq / sampleRate

        for (i in samples.indices) {
            val carrier = sin(i * step).toFloat()
            val original = samples[i].toFloat() / 32768f
            val modulated = (original * carrier * 1.5f).coerceIn(-1.0f, 1.0f)
            output[i] = (modulated * 32767f).toInt().toShort()
        }
        return output
    }

    /**
     * Vocal enhancer: gentle low-cut (<80Hz), presence boost (3-5kHz), and gentle high-air.
     */
    fun applyVocalEnhancer(samples: ShortArray): ShortArray {
        val output = ShortArray(samples.size)
        // High-pass filter (remove rumble) + high shelf presence
        var prevIn = 0f
        var prevOut = 0f
        val alpha = 0.95f // High-pass coefficient

        for (i in samples.indices) {
            val currentIn = samples[i].toFloat()
            val highPassed = alpha * (prevOut + currentIn - prevIn)
            prevIn = currentIn
            prevOut = highPassed

            // Presence boost + soft saturation
            val boosted = highPassed * 1.25f
            output[i] = softSaturate(boosted).coerceIn(-32768f, 32767f).toInt().toShort()
        }
        return output
    }

    /**
     * Radio / Megaphone bandpass effect with drive.
     */
    fun applyRadioBandpass(samples: ShortArray, sampleRate: Int): ShortArray {
        val output = ShortArray(samples.size)
        var prev = 0f
        for (i in samples.indices) {
            val x = samples[i].toFloat() / 32768f
            // Simple bandpass filter
            val filtered = 0.5f * (x - prev)
            prev = x
            // Add drive / clipping distortion
            val driven = (filtered * 4f).coerceIn(-0.85f, 0.85f) * 1.15f
            output[i] = (driven * 32767f).toInt().toShort()
        }
        return output
    }

    /**
     * Studio Echo / Delay effect.
     */
    fun applyEcho(
        samples: ShortArray,
        sampleRate: Int,
        delayMs: Int = 250,
        feedback: Float = 0.4f,
        wet: Float = 0.4f
    ): ShortArray {
        val delaySamples = (sampleRate * delayMs / 1000).coerceAtLeast(1)
        val output = ShortArray(samples.size)
        val delayBuffer = FloatArray(delaySamples)
        var delayIdx = 0

        for (i in samples.indices) {
            val input = samples[i].toFloat()
            val delayed = delayBuffer[delayIdx]
            val out = input * (1f - wet * 0.5f) + delayed * wet

            delayBuffer[delayIdx] = input + delayed * feedback
            delayIdx = (delayIdx + 1) % delaySamples

            output[i] = out.toInt().coerceIn(-32768, 32767).toShort()
        }
        return output
    }

    /**
     * Studio Reverb (comb filters + all-pass simulation).
     */
    fun applyReverb(
        samples: ShortArray,
        sampleRate: Int,
        decay: Float = 0.5f,
        wetLevel: Float = 0.35f
    ): ShortArray {
        val delays = intArrayOf(
            (sampleRate * 0.029f).toInt(),
            (sampleRate * 0.037f).toInt(),
            (sampleRate * 0.044f).toInt()
        )
        val buffers = Array(delays.size) { FloatArray(delays[it]) }
        val indices = IntArray(delays.size)
        val output = ShortArray(samples.size)

        for (i in samples.indices) {
            val input = samples[i].toFloat()
            var combSum = 0f

            for (b in delays.indices) {
                val delayLen = delays[b]
                val idx = indices[b]
                val delayedSample = buffers[b][idx]
                combSum += delayedSample
                buffers[b][idx] = input + delayedSample * decay
                indices[b] = (idx + 1) % delayLen
            }

            val mixed = input * (1f - wetLevel) + (combSum / delays.size) * wetLevel
            output[i] = mixed.toInt().coerceIn(-32768, 32767).toShort()
        }
        return output
    }

    /**
     * Studio 5-band Equalizer (-12dB to +12dB).
     */
    fun applyEqualizer(
        samples: ShortArray,
        sampleRate: Int,
        band1GainDb: Float, // 100 Hz
        band2GainDb: Float, // 400 Hz
        band3GainDb: Float, // 1 kHz
        band4GainDb: Float, // 4 kHz
        band5GainDb: Float  // 12 kHz
    ): ShortArray {
        // Fast multi-band approximation
        val g1 = 10.0.pow(band1GainDb / 20.0).toFloat()
        val g2 = 10.0.pow(band2GainDb / 20.0).toFloat()
        val g3 = 10.0.pow(band3GainDb / 20.0).toFloat()
        val g4 = 10.0.pow(band4GainDb / 20.0).toFloat()
        val g5 = 10.0.pow(band5GainDb / 20.0).toFloat()

        if (abs(g1 - 1f) < 0.05f && abs(g2 - 1f) < 0.05f && abs(g3 - 1f) < 0.05f &&
            abs(g4 - 1f) < 0.05f && abs(g5 - 1f) < 0.05f) {
            return samples.copyOf()
        }

        val output = ShortArray(samples.size)
        var s1 = 0f
        var s2 = 0f
        var s3 = 0f
        var s4 = 0f

        for (i in samples.indices) {
            val x = samples[i].toFloat()

            // 4-stage low-pass splitting
            s1 += 0.03f * (x - s1)
            s2 += 0.12f * (x - s2)
            s3 += 0.35f * (x - s3)
            s4 += 0.70f * (x - s4)

            val low = s1
            val lowMid = s2 - s1
            val mid = s3 - s2
            val highMid = s4 - s3
            val high = x - s4

            val eq = low * g1 + lowMid * g2 + mid * g3 + highMid * g4 + high * g5
            output[i] = softSaturate(eq).coerceIn(-32768f, 32767f).toInt().toShort()
        }
        return output
    }

    /**
     * Compressor and Limiter.
     */
    fun applyCompressor(
        samples: ShortArray,
        thresholdDb: Float = -16f,
        ratio: Float = 3f,
        makeupGain: Float = 1.25f
    ): ShortArray {
        val output = ShortArray(samples.size)
        val thresholdAmp = (10.0.pow(thresholdDb / 20.0) * 32768.0).toFloat()
        var envelope = 0f
        val attackCoeff = 0.1f
        val releaseCoeff = 0.01f

        for (i in samples.indices) {
            val input = samples[i].toFloat()
            val absInput = abs(input)

            envelope += if (absInput > envelope) {
                attackCoeff * (absInput - envelope)
            } else {
                releaseCoeff * (absInput - envelope)
            }

            var gain = 1.0f
            if (envelope > thresholdAmp) {
                val overDb = 20.0 * log10(envelope / thresholdAmp)
                val gainReductionDb = overDb * (1.0 - 1.0 / ratio)
                gain = 10.0.pow(-gainReductionDb / 20.0).toFloat()
            }

            val compressed = input * gain * makeupGain
            output[i] = softSaturate(compressed).coerceIn(-32768f, 32767f).toInt().toShort()
        }
        return output
    }

    /**
     * Noise Gate to eliminate background hiss/noise in pauses.
     */
    fun applyNoiseGate(samples: ShortArray, thresholdDb: Float = -45f): ShortArray {
        val thresholdAmp = (10.0.pow(thresholdDb / 20.0) * 32768.0).toFloat()
        val output = ShortArray(samples.size)
        var gate = 1.0f
        val attack = 0.2f
        val release = 0.02f

        for (i in samples.indices) {
            val sample = samples[i].toFloat()
            val targetGate = if (abs(sample) >= thresholdAmp) 1.0f else 0.0f
            gate += if (targetGate > gate) attack * (targetGate - gate) else release * (targetGate - gate)
            output[i] = (sample * gate).toInt().toShort()
        }
        return output
    }

    /**
     * Studio Normalization: brings maximum peak amplitude to -0.2 dB (0.977 of 32767).
     */
    fun normalizeAudio(samples: ShortArray): ShortArray {
        if (samples.isEmpty()) return samples.copyOf()
        var maxPeak = 0
        for (s in samples) {
            val a = abs(s.toInt())
            if (a > maxPeak) maxPeak = a
        }
        if (maxPeak <= 10) return samples.copyOf()

        val targetMax = 32000f
        val multiplier = (targetMax / maxPeak.toFloat()).coerceIn(0.5f, 5.0f)
        val output = ShortArray(samples.size)
        for (i in samples.indices) {
            output[i] = (samples[i] * multiplier).toInt().coerceIn(-32768, 32767).toShort()
        }
        return output
    }

    /**
     * Automatically trims silence below threshold from head and tail.
     */
    fun trimSilence(samples: ShortArray, thresholdDb: Float = -42f): ShortArray {
        if (samples.isEmpty()) return samples.copyOf()
        val thresholdAmp = (10.0.pow(thresholdDb / 20.0) * 32768.0).toInt()

        var firstNonSilent = 0
        while (firstNonSilent < samples.size && abs(samples[firstNonSilent].toInt()) < thresholdAmp) {
            firstNonSilent++
        }

        var lastNonSilent = samples.size - 1
        while (lastNonSilent > firstNonSilent && abs(samples[lastNonSilent].toInt()) < thresholdAmp) {
            lastNonSilent--
        }

        if (firstNonSilent >= lastNonSilent) return samples.copyOf()

        val trimmed = ShortArray(lastNonSilent - firstNonSilent + 1)
        System.arraycopy(samples, firstNonSilent, trimmed, 0, trimmed.size)
        return trimmed
    }

    /**
     * Vocal Pitch Quantization / Auto-Tune:
     * Corrects vocal micro-cents deviation towards nearest 12-TET scale note.
     */
    fun pitchQuantizeAutoTune(samples: ShortArray, sampleRate: Int): ShortArray {
        if (samples.size < 2048) return samples.copyOf()
        val pitchInfo = AudioPitchDetector.detectPitch(samples, sampleRate) ?: return samples.copyOf()

        // Calculate pitch correction ratio
        val cents = pitchInfo.centsOffset
        if (abs(cents) < 5f) return samples.copyOf() // already in tune

        val shiftFactor = 2.0.pow(-cents / 1200.0).toFloat()
        return pitchShift(samples, shiftFactor)
    }

    /**
     * Smooth tanh-like soft saturation to avoid digital clipping clicks.
     */
    private fun softSaturate(input: Float): Float {
        val norm = input / 32768f
        val saturated = if (abs(norm) < 0.75f) {
            norm
        } else if (norm > 0) {
            0.75f + (1f - 0.75f) * (1f - exp(-(norm - 0.75f) / 0.25f))
        } else {
            -0.75f - (1f - 0.75f) * (1f - exp(-(-norm - 0.75f) / 0.25f))
        }
        return saturated * 32767f
    }
}
