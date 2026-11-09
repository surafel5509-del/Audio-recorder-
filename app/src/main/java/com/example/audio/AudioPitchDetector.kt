package com.example.audio

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

data class PitchInfo(
    val frequencyHz: Float,
    val noteName: String,
    val centsOffset: Float, // -50.0f .. +50.0f
    val isInTune: Boolean
)

object AudioPitchDetector {

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /**
     * Pitch detection using normalized autocorrelation.
     * Detects fundamental frequency in human vocal range (65 Hz to 1100 Hz).
     */
    fun detectPitch(samples: ShortArray, sampleRate: Int): PitchInfo? {
        if (samples.size < 1024) return null

        val minFreq = 65f   // ~C2
        val maxFreq = 1100f // ~C6
        val minLag = (sampleRate / maxFreq).toInt()
        val maxLag = (sampleRate / minFreq).toInt().coerceAtMost(samples.size / 2)

        var bestLag = -1
        var bestCorrelation = 0.0
        var totalEnergy = 0.0

        for (i in 0 until maxLag) {
            val s = samples[i].toDouble()
            totalEnergy += s * s
        }

        if (totalEnergy < 50000.0) return null // Below threshold (silence/noise)

        for (lag in minLag..maxLag) {
            var sum = 0.0
            val len = samples.size - lag
            for (i in 0 until minOf(len, 1024)) {
                sum += samples[i].toDouble() * samples[i + lag].toDouble()
            }
            if (sum > bestCorrelation) {
                bestCorrelation = sum
                bestLag = lag
            }
        }

        if (bestLag <= 0 || bestCorrelation / totalEnergy < 0.35) return null

        val freq = sampleRate.toFloat() / bestLag
        if (freq < minFreq || freq > maxFreq) return null

        // Convert Hz to MIDI note: note = 69 + 12 * log2(freq / 440)
        val midiNoteFloat = 69.0 + 12.0 * (log2(freq.toDouble() / 440.0))
        val roundedMidi = midiNoteFloat.roundToInt()
        val cents = ((midiNoteFloat - roundedMidi) * 100.0).toFloat().coerceIn(-50f, 50f)

        val noteIndex = (roundedMidi % 12).let { if (it < 0) it + 12 else it }
        val octave = (roundedMidi / 12) - 1
        val noteName = "${NOTE_NAMES[noteIndex]}$octave"
        val isInTune = abs(cents) < 10f

        return PitchInfo(
            frequencyHz = freq,
            noteName = noteName,
            centsOffset = cents,
            isInTune = isInTune
        )
    }
}
