package com.example.audio

import java.io.File
import kotlin.math.max
import kotlin.math.min

class AudioEditorEngine {

    private val undoStack = mutableListOf<ShortArray>()
    private val redoStack = mutableListOf<ShortArray>()

    var currentSamples: ShortArray = ShortArray(0)
        private set

    var clipboard: ShortArray? = null
        private set

    var sampleRate: Int = 44100
        private set

    var channels: Int = 1
        private set

    fun loadWavFile(wavFile: File): Boolean {
        val info = WavUtil.parseWavHeader(wavFile) ?: return false
        val samples = WavUtil.readPcmSamples(wavFile)
        this.sampleRate = info.sampleRate
        this.channels = info.channels
        this.currentSamples = samples
        undoStack.clear()
        redoStack.clear()
        return true
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    private fun pushUndo() {
        undoStack.add(currentSamples.copyOf())
        redoStack.clear()
        if (undoStack.size > 20) {
            undoStack.removeAt(0)
        }
    }

    fun undo(): Boolean {
        if (!canUndo()) return false
        redoStack.add(currentSamples.copyOf())
        currentSamples = undoStack.removeAt(undoStack.size - 1)
        return true
    }

    fun redo(): Boolean {
        if (!canRedo()) return false
        undoStack.add(currentSamples.copyOf())
        currentSamples = redoStack.removeAt(redoStack.size - 1)
        return true
    }

    fun getDurationMs(): Long {
        val bytesPerSec = sampleRate * channels * 2
        return if (bytesPerSec > 0) (currentSamples.size * 2 * 1000L) / bytesPerSec else 0L
    }

    private fun msToSampleIndex(ms: Long): Int {
        val samplesPerSec = sampleRate * channels
        val idx = ((ms * samplesPerSec) / 1000L).toInt()
        return idx.coerceIn(0, currentSamples.size)
    }

    /**
     * Trim: keep only the section between startMs and endMs.
     */
    fun trim(startMs: Long, endMs: Long): Boolean {
        if (currentSamples.isEmpty() || startMs >= endMs) return false
        val startIdx = msToSampleIndex(startMs)
        val endIdx = msToSampleIndex(endMs)
        if (startIdx >= endIdx) return false

        pushUndo()
        val trimmed = ShortArray(endIdx - startIdx)
        System.arraycopy(currentSamples, startIdx, trimmed, 0, trimmed.size)
        currentSamples = trimmed
        return true
    }

    /**
     * Cut: remove the section between startMs and endMs, saving removed section to clipboard.
     */
    fun cut(startMs: Long, endMs: Long): Boolean {
        if (currentSamples.isEmpty() || startMs >= endMs) return false
        val startIdx = msToSampleIndex(startMs)
        val endIdx = msToSampleIndex(endMs)
        if (startIdx >= endIdx) return false

        pushUndo()
        val cutLen = endIdx - startIdx
        val cutSamples = ShortArray(cutLen)
        System.arraycopy(currentSamples, startIdx, cutSamples, 0, cutLen)
        clipboard = cutSamples

        val remaining = ShortArray(currentSamples.size - cutLen)
        System.arraycopy(currentSamples, 0, remaining, 0, startIdx)
        System.arraycopy(currentSamples, endIdx, remaining, startIdx, currentSamples.size - endIdx)
        currentSamples = remaining
        return true
    }

    /**
     * Copy: copy section between startMs and endMs into clipboard.
     */
    fun copy(startMs: Long, endMs: Long): Boolean {
        if (currentSamples.isEmpty() || startMs >= endMs) return false
        val startIdx = msToSampleIndex(startMs)
        val endIdx = msToSampleIndex(endMs)
        if (startIdx >= endIdx) return false

        val copyLen = endIdx - startIdx
        val copySamples = ShortArray(copyLen)
        System.arraycopy(currentSamples, startIdx, copySamples, 0, copyLen)
        clipboard = copySamples
        return true
    }

    /**
     * Paste: insert clipboard contents at targetMs.
     */
    fun paste(targetMs: Long): Boolean {
        val clip = clipboard ?: return false
        if (clip.isEmpty()) return false

        pushUndo()
        val insertIdx = msToSampleIndex(targetMs)
        val result = ShortArray(currentSamples.size + clip.size)
        System.arraycopy(currentSamples, 0, result, 0, insertIdx)
        System.arraycopy(clip, 0, result, insertIdx, clip.size)
        System.arraycopy(currentSamples, insertIdx, result, insertIdx + clip.size, currentSamples.size - insertIdx)
        currentSamples = result
        return true
    }

    /**
     * Fade In over durationMs.
     */
    fun fadeIn(durationMs: Long = 1000L): Boolean {
        if (currentSamples.isEmpty()) return false
        pushUndo()
        val fadeSamples = msToSampleIndex(durationMs).coerceAtMost(currentSamples.size)
        for (i in 0 until fadeSamples) {
            val factor = i.toFloat() / fadeSamples
            currentSamples[i] = (currentSamples[i] * factor).toInt().toShort()
        }
        return true
    }

    /**
     * Fade Out over durationMs.
     */
    fun fadeOut(durationMs: Long = 1000L): Boolean {
        if (currentSamples.isEmpty()) return false
        pushUndo()
        val fadeSamples = msToSampleIndex(durationMs).coerceAtMost(currentSamples.size)
        val startIdx = currentSamples.size - fadeSamples
        for (i in 0 until fadeSamples) {
            val factor = 1.0f - (i.toFloat() / fadeSamples)
            currentSamples[startIdx + i] = (currentSamples[startIdx + i] * factor).toInt().toShort()
        }
        return true
    }

    /**
     * Speed control (resampling interpolation).
     */
    fun changeSpeed(speedMultiplier: Float): Boolean {
        if (currentSamples.isEmpty() || speedMultiplier <= 0f) return false
        pushUndo()
        currentSamples = AudioEffectsProcessor.pitchShift(currentSamples, speedMultiplier)
        return true
    }

    /**
     * Applies an EQ preset or custom EQ to current audio.
     */
    fun applyEqualizer(b1: Float, b2: Float, b3: Float, b4: Float, b5: Float): Boolean {
        if (currentSamples.isEmpty()) return false
        pushUndo()
        currentSamples = AudioEffectsProcessor.applyEqualizer(
            samples = currentSamples,
            sampleRate = sampleRate,
            band1GainDb = b1,
            band2GainDb = b2,
            band3GainDb = b3,
            band4GainDb = b4,
            band5GainDb = b5
        )
        return true
    }

    /**
     * Applies voice changer preset.
     */
    fun applyVoicePreset(preset: VoiceEffectPreset): Boolean {
        if (currentSamples.isEmpty()) return false
        pushUndo()
        currentSamples = AudioEffectsProcessor.applyVoiceEffect(currentSamples, sampleRate, preset)
        return true
    }

    /**
     * Normalizes audio to -0.2 dB peak loudness.
     */
    fun normalize(): Boolean {
        if (currentSamples.isEmpty()) return false
        pushUndo()
        currentSamples = AudioEffectsProcessor.normalizeAudio(currentSamples)
        return true
    }

    /**
     * Trims leading and trailing silence.
     */
    fun trimSilence(): Boolean {
        if (currentSamples.isEmpty()) return false
        pushUndo()
        currentSamples = AudioEffectsProcessor.trimSilence(currentSamples)
        return true
    }

    /**
     * Applies vocal pitch quantization (Auto-Tune).
     */
    fun applyAutoTune(): Boolean {
        if (currentSamples.isEmpty()) return false
        pushUndo()
        currentSamples = AudioEffectsProcessor.pitchQuantizeAutoTune(currentSamples, sampleRate)
        return true
    }

    /**
     * Saves current samples to destination WAV file.
     */
    fun exportToWav(destinationFile: File): Boolean {
        if (currentSamples.isEmpty()) return false
        return try {
            WavUtil.writePcmSamplesToWav(
                wavFile = destinationFile,
                samples = currentSamples,
                sampleRate = sampleRate,
                channels = channels
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Merges multiple WAV files into one.
     */
    fun mergeFiles(wavFiles: List<File>, destinationFile: File): Boolean {
        if (wavFiles.isEmpty()) return false
        val allSamples = mutableListOf<Short>()
        var targetSampleRate = 44100
        var targetChannels = 1

        for (file in wavFiles) {
            val info = WavUtil.parseWavHeader(file) ?: continue
            targetSampleRate = info.sampleRate
            targetChannels = info.channels
            val samples = WavUtil.readPcmSamples(file)
            for (s in samples) allSamples.add(s)
        }

        if (allSamples.isEmpty()) return false
        val mergedArray = ShortArray(allSamples.size) { allSamples[it] }
        return try {
            WavUtil.writePcmSamplesToWav(
                wavFile = destinationFile,
                samples = mergedArray,
                sampleRate = targetSampleRate,
                channels = targetChannels
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
