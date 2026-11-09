package com.example.audio

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

object WavUtil {

    /**
     * Writes standard 44-byte WAV header for 16-bit PCM audio.
     */
    fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        totalDataLen: Long,
        sampleRate: Long,
        channels: Int,
        byteRate: Long
    ) {
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        out.write(header, 0, 44)
    }

    /**
     * Converts a raw PCM file to standard WAV file with proper header.
     */
    fun convertPcmToWav(
        pcmFile: File,
        wavFile: File,
        sampleRate: Int,
        channels: Int
    ) {
        val totalAudioLen = pcmFile.length()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * 16 / 8).toLong()

        FileInputStream(pcmFile).use { inStream ->
            FileOutputStream(wavFile).use { outStream ->
                writeWavHeader(
                    out = outStream,
                    totalAudioLen = totalAudioLen,
                    totalDataLen = totalDataLen,
                    sampleRate = sampleRate.toLong(),
                    channels = channels,
                    byteRate = byteRate
                )
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                    outStream.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    data class WavInfo(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val durationMs: Long,
        val dataOffset: Int,
        val dataSize: Long
    )

    fun parseWavHeader(file: File): WavInfo? {
        if (!file.exists() || file.length() < 44) return null
        return try {
            val bytes = ByteArray(44)
            FileInputStream(file).use { it.read(bytes) }
            val riff = String(bytes, 0, 4)
            val wave = String(bytes, 8, 4)
            if (riff != "RIFF" || wave != "WAVE") return null

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val channels = buffer.getShort(22).toInt()
            val sampleRate = buffer.getInt(24)
            val bitsPerSample = buffer.getShort(34).toInt()
            val dataSize = file.length() - 44
            val bytesPerSecond = sampleRate * channels * (bitsPerSample / 8)
            val durationMs = if (bytesPerSecond > 0) (dataSize * 1000L) / bytesPerSecond else 0L

            WavInfo(
                sampleRate = sampleRate,
                channels = channels,
                bitsPerSample = bitsPerSample,
                durationMs = durationMs,
                dataOffset = 44,
                dataSize = dataSize
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads PCM 16-bit short samples from a WAV file (skipping 44-byte header).
     */
    fun readPcmSamples(wavFile: File): ShortArray {
        if (!wavFile.exists() || wavFile.length() <= 44) return ShortArray(0)
        val dataLen = (wavFile.length() - 44).toInt()
        val numSamples = dataLen / 2
        val samples = ShortArray(numSamples)

        val byteBuffer = ByteBuffer.allocate(dataLen).order(ByteOrder.LITTLE_ENDIAN)
        FileInputStream(wavFile).use { input ->
            input.skip(44)
            val bytes = ByteArray(dataLen)
            input.read(bytes)
            byteBuffer.put(bytes)
        }
        byteBuffer.flip()
        byteBuffer.asShortBuffer().get(samples)
        return samples
    }

    /**
     * Writes 16-bit short samples into a clean WAV file.
     */
    fun writePcmSamplesToWav(
        wavFile: File,
        samples: ShortArray,
        sampleRate: Int,
        channels: Int
    ) {
        val totalAudioLen = (samples.size * 2).toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * 16 / 8).toLong()

        FileOutputStream(wavFile).use { outStream ->
            writeWavHeader(
                out = outStream,
                totalAudioLen = totalAudioLen,
                totalDataLen = totalDataLen,
                sampleRate = sampleRate.toLong(),
                channels = channels,
                byteRate = byteRate
            )
            val byteBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                byteBuffer.putShort(sample)
            }
            outStream.write(byteBuffer.array())
        }
    }

    /**
     * Extracts normalized amplitude points (0..100) for fast waveform drawing.
     */
    fun extractWaveformPoints(wavFile: File, pointCount: Int = 80): List<Int> {
        val samples = readPcmSamples(wavFile)
        if (samples.isEmpty()) return List(pointCount) { 10 }

        val result = mutableListOf<Int>()
        val step = max(1, samples.size / pointCount)
        var maxGlobal = 1

        for (i in 0 until pointCount) {
            val startIdx = i * step
            val endIdx = minOf(samples.size, startIdx + step)
            var maxChunk = 0
            for (j in startIdx until endIdx) {
                val amp = abs(samples[j].toInt())
                if (amp > maxChunk) maxChunk = amp
            }
            if (maxChunk > maxGlobal) maxGlobal = maxChunk
            result.add(maxChunk)
        }

        // Normalize 0..100 with minimum height for aesthetics
        val normalizer = if (maxGlobal > 0) maxGlobal.toFloat() else 1f
        return result.map { raw ->
            val norm = ((raw / normalizer) * 92f + 8f).toInt()
            norm.coerceIn(8, 100)
        }
    }
}
