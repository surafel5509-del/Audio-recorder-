package com.example.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.*

object StudioBackingTracks {

    data class BackingTrackInfo(
        val id: String,
        val name: String,
        val description: String,
        val durationSec: Int,
        val fileName: String,
        val genre: String,
        val colorHex: String
    )

    val PRESET_BACKING_TRACKS = listOf(
        BackingTrackInfo(
            id = "backing_guitar",
            name = "Acoustic Guitar Chords",
            description = "Warm strummed acoustic rhythm in C Major (C - G - Am - F)",
            durationSec = 16,
            fileName = "backing_acoustic_guitar.wav",
            genre = "Acoustic",
            colorHex = "#FF9800"
        ),
        BackingTrackInfo(
            id = "backing_lofi",
            name = "Lo-Fi Chill Beat",
            description = "Mellow hip-hop drum groove with warm sub bass and hi-hats",
            durationSec = 16,
            fileName = "backing_lofi_beat.wav",
            genre = "Lo-Fi",
            colorHex = "#9C27B0"
        ),
        BackingTrackInfo(
            id = "backing_piano",
            name = "Lush Studio Piano",
            description = "Grand piano arpeggios and ballad chord progression",
            durationSec = 16,
            fileName = "backing_studio_piano.wav",
            genre = "Classical/Pop",
            colorHex = "#00BCD4"
        ),
        BackingTrackInfo(
            id = "backing_synth",
            name = "Cyber Synthwave Beat",
            description = "80s retro synth bassline and punchy electronic drums",
            durationSec = 16,
            fileName = "backing_synth_groove.wav",
            genre = "Electronic",
            colorHex = "#E91E63"
        )
    )

    suspend fun getOrCreateTrackFile(context: Context, info: BackingTrackInfo): File = withContext(Dispatchers.IO) {
        val backingDir = File(context.filesDir, "backing_tracks").apply { mkdirs() }
        val targetFile = File(backingDir, info.fileName)
        if (targetFile.exists() && targetFile.length() > 1000) {
            return@withContext targetFile
        }

        val sampleRate = 44100
        val totalSamples = sampleRate * info.durationSec
        val samples = ShortArray(totalSamples)

        when (info.id) {
            "backing_guitar" -> synthesizeGuitarProgression(samples, sampleRate)
            "backing_lofi" -> synthesizeLofiBeat(samples, sampleRate)
            "backing_piano" -> synthesizePianoProgression(samples, sampleRate)
            else -> synthesizeSynthwave(samples, sampleRate)
        }

        WavUtil.writePcmSamplesToWav(
            wavFile = targetFile,
            samples = samples,
            sampleRate = sampleRate,
            channels = 1
        )
        targetFile
    }

    private fun synthesizeGuitarProgression(samples: ShortArray, sampleRate: Int) {
        // C - G - Am - F progression (4 seconds each)
        val chordRootFreqs = floatArrayOf(130.81f, 98.0f, 110.0f, 87.31f) // C3, G2, A2, F2
        val chordIntervals = arrayOf(
            floatArrayOf(1f, 1.25f, 1.5f, 2f),      // Major
            floatArrayOf(1f, 1.25f, 1.5f, 1.875f),  // Major 7
            floatArrayOf(1f, 1.2f, 1.5f, 2f),       // Minor
            floatArrayOf(1f, 1.25f, 1.5f, 2f)       // Major
        )

        val barSamples = sampleRate * 4
        for (i in samples.indices) {
            val barIndex = (i / barSamples) % 4
            val inBar = i % barSamples
            val root = chordRootFreqs[barIndex]
            val intervals = chordIntervals[barIndex]

            // Strum rhythm: 2 strums per second
            val beatSamples = sampleRate / 2
            val inBeat = inBar % beatSamples
            val beatDecay = exp(-inBeat.toFloat() / (sampleRate * 0.35f))

            var signal = 0f
            for ((noteIdx, ratio) in intervals.withIndex()) {
                val freq = root * ratio
                val noteOffset = noteIdx * (sampleRate / 80) // Strum spread
                val noteTime = (inBeat - noteOffset).coerceAtLeast(0)
                val decay = exp(-noteTime.toFloat() / (sampleRate * 0.4f))
                val wave = sin(2.0 * PI * freq * i / sampleRate).toFloat() +
                        0.4f * sin(4.0 * PI * freq * i / sampleRate).toFloat() +
                        0.15f * sin(6.0 * PI * freq * i / sampleRate).toFloat()
                signal += wave * decay
            }
            val mix = signal * beatDecay * 4500f
            samples[i] = mix.toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun synthesizeLofiBeat(samples: ShortArray, sampleRate: Int) {
        val bpm = 80
        val beatSamples = (sampleRate * 60) / bpm // ~33075 samples
        val subBeat = beatSamples / 4

        for (i in samples.indices) {
            val inBar = i % (beatSamples * 4)
            val beatNum = (inBar / beatSamples)

            var kick = 0f
            var snare = 0f
            var hihat = 0f
            var subBass = 0f

            // Kick on beat 0 and beat 2.5
            val kickTime1 = inBar
            val kickTime2 = inBar - (beatSamples * 2 + beatSamples / 2)
            if (kickTime1 in 0 until (sampleRate / 4)) {
                val f = 120f * exp(-kickTime1.toFloat() / (sampleRate * 0.05f))
                kick += sin(2.0 * PI * f * kickTime1 / sampleRate).toFloat() * exp(-kickTime1.toFloat() / (sampleRate * 0.12f))
            }
            if (kickTime2 in 0 until (sampleRate / 4)) {
                val f = 110f * exp(-kickTime2.toFloat() / (sampleRate * 0.05f))
                kick += sin(2.0 * PI * f * kickTime2 / sampleRate).toFloat() * exp(-kickTime2.toFloat() / (sampleRate * 0.12f))
            }

            // Snare on beat 1 and beat 3
            val snareTime1 = inBar - beatSamples
            val snareTime2 = inBar - (beatSamples * 3)
            if (snareTime1 in 0 until (sampleRate / 3)) {
                val noise = ((i * 1103515245 + 12345) % 65536) / 32768f - 1f
                snare += noise * exp(-snareTime1.toFloat() / (sampleRate * 0.08f)) * 0.7f
            }
            if (snareTime2 in 0 until (sampleRate / 3)) {
                val noise = ((i * 1103515245 + 12345) % 65536) / 32768f - 1f
                snare += noise * exp(-snareTime2.toFloat() / (sampleRate * 0.08f)) * 0.7f
            }

            // Hi-hats every 16th note
            val in16th = inBar % subBeat
            if (in16th in 0 until (sampleRate / 16)) {
                val noise = ((i * 1664525 + 1013904223) % 65536) / 32768f - 1f
                hihat += noise * exp(-in16th.toFloat() / (sampleRate * 0.02f)) * 0.3f
            }

            // Warm sub-bass note (F - C - G - D)
            val bassFreq = when (beatNum) {
                0 -> 55.0f
                1 -> 65.4f
                2 -> 49.0f
                else -> 73.4f
            }
            subBass = sin(2.0 * PI * bassFreq * i / sampleRate).toFloat() * 0.5f

            val total = (kick * 1.1f + snare * 0.9f + hihat * 0.5f + subBass * 0.6f) * 16000f
            samples[i] = total.toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun synthesizePianoProgression(samples: ShortArray, sampleRate: Int) {
        // C - Em - F - G progression
        val chords = arrayOf(
            floatArrayOf(261.63f, 329.63f, 392.00f, 523.25f), // C Major
            floatArrayOf(164.81f, 196.00f, 246.94f, 329.63f), // E Minor
            floatArrayOf(174.61f, 220.00f, 261.63f, 349.23f), // F Major
            floatArrayOf(196.00f, 246.94f, 293.66f, 392.00f)  // G Major
        )
        val barSamples = sampleRate * 4

        for (i in samples.indices) {
            val barIdx = (i / barSamples) % 4
            val inBar = i % barSamples
            val chord = chords[barIdx]

            // Arpeggio note every 0.5s
            val noteIdx = ((inBar / (sampleRate / 2)) % chord.size)
            val noteFreq = chord[noteIdx]
            val inNote = inBar % (sampleRate / 2)
            val noteDecay = exp(-inNote.toFloat() / (sampleRate * 0.4f))

            // Piano overtone harmonics
            val wave = sin(2.0 * PI * noteFreq * i / sampleRate).toFloat() +
                    0.5f * sin(4.0 * PI * noteFreq * i / sampleRate).toFloat() +
                    0.25f * sin(6.0 * PI * noteFreq * i / sampleRate).toFloat() +
                    0.1f * sin(8.0 * PI * noteFreq * i / sampleRate).toFloat()

            val sampleVal = wave * noteDecay * 11000f
            samples[i] = sampleVal.toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun synthesizeSynthwave(samples: ShortArray, sampleRate: Int) {
        val bpm = 110
        val beat = (sampleRate * 60) / bpm
        for (i in samples.indices) {
            val inBeat = i % beat
            // 80s bassline octave bounce
            val octave = if ((i / (beat / 2)) % 2 == 0) 1f else 2f
            val freq = 65.41f * octave // C2 / C3
            val saw = 2f * ((freq * i / sampleRate) % 1f) - 1f
            val env = exp(-inBeat.toFloat() / (beat * 0.6f))
            val kick = if (inBeat < beat / 4) {
                sin(2.0 * PI * 130f * exp(-inBeat.toFloat() / (sampleRate * 0.04f)) * inBeat / sampleRate).toFloat()
            } else 0f
            val mix = (saw * env * 0.6f + kick * 0.8f) * 14000f
            samples[i] = mix.toInt().coerceIn(-32768, 32767).toShort()
        }
    }
}
