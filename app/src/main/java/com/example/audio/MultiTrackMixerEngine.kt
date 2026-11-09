package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.max

data class MixerTrackData(
    val id: Long,
    val name: String,
    val filePath: String,
    val volume: Float = 1.0f, // 0.0f .. 1.5f
    val pan: Float = 0.0f,    // -1.0f (Left) .. +1.0f (Right)
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val offsetMs: Long = 0L,
    val colorHex: String = "#FF5722",
    var samples: ShortArray = ShortArray(0),
    var sampleRate: Int = 44100,
    var channels: Int = 1
)

class MultiTrackMixerEngine {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(0L)
    val totalDurationMs: StateFlow<Long> = _totalDurationMs.asStateFlow()

    private var playbackJob: Job? = null
    private var audioTrackPlayer: AudioTrack? = null

    val tracks = mutableListOf<MixerTrackData>()

    fun loadTrack(
        id: Long,
        name: String,
        file: File,
        volume: Float = 1.0f,
        pan: Float = 0.0f,
        offsetMs: Long = 0L,
        colorHex: String = "#FF5722"
    ): Boolean {
        if (!file.exists()) return false
        val info = WavUtil.parseWavHeader(file) ?: return false
        val samples = WavUtil.readPcmSamples(file)

        val trackData = MixerTrackData(
            id = id,
            name = name,
            filePath = file.absolutePath,
            volume = volume,
            pan = pan,
            offsetMs = offsetMs,
            colorHex = colorHex,
            samples = samples,
            sampleRate = info.sampleRate,
            channels = info.channels
        )
        tracks.removeAll { it.id == id }
        tracks.add(trackData)
        recalculateTotalDuration()
        return true
    }

    fun removeTrack(id: Long) {
        tracks.removeAll { it.id == id }
        recalculateTotalDuration()
    }

    fun updateTrackVolume(id: Long, volume: Float) {
        val idx = tracks.indexOfFirst { it.id == id }
        if (idx != -1) {
            tracks[idx] = tracks[idx].copy(volume = volume)
        }
    }

    fun updateTrackPan(id: Long, pan: Float) {
        val idx = tracks.indexOfFirst { it.id == id }
        if (idx != -1) {
            tracks[idx] = tracks[idx].copy(pan = pan)
        }
    }

    fun toggleTrackMute(id: Long) {
        val idx = tracks.indexOfFirst { it.id == id }
        if (idx != -1) {
            tracks[idx] = tracks[idx].copy(isMuted = !tracks[idx].isMuted)
        }
    }

    fun toggleTrackSolo(id: Long) {
        val idx = tracks.indexOfFirst { it.id == id }
        if (idx != -1) {
            val wasSolo = tracks[idx].isSolo
            tracks[idx] = tracks[idx].copy(isSolo = !wasSolo)
        }
    }

    fun updateTrackOffset(id: Long, offsetMs: Long) {
        val idx = tracks.indexOfFirst { it.id == id }
        if (idx != -1) {
            tracks[idx] = tracks[idx].copy(offsetMs = offsetMs.coerceAtLeast(0L))
            recalculateTotalDuration()
        }
    }

    private fun recalculateTotalDuration() {
        var maxMs = 0L
        for (t in tracks) {
            val trackMs = if (t.sampleRate > 0 && t.channels > 0) {
                (t.samples.size * 1000L) / (t.sampleRate * t.channels)
            } else 0L
            val endMs = t.offsetMs + trackMs
            if (endMs > maxMs) maxMs = endMs
        }
        _totalDurationMs.value = maxMs
    }

    /**
     * Renders all active tracks into a mixed master stereo PCM ShortArray.
     */
    fun renderMix(targetSampleRate: Int = 44100): ShortArray {
        recalculateTotalDuration()
        val totalMs = _totalDurationMs.value
        if (totalMs <= 0 || tracks.isEmpty()) return ShortArray(0)

        // Master output is Stereo (2 channels)
        val totalStereoFrames = ((totalMs * targetSampleRate) / 1000L).toInt()
        val masterL = FloatArray(totalStereoFrames)
        val masterR = FloatArray(totalStereoFrames)

        val hasSolo = tracks.any { it.isSolo }

        for (track in tracks) {
            if (hasSolo && !track.isSolo) continue
            if (!hasSolo && track.isMuted) continue
            if (track.samples.isEmpty()) continue

            val trackOffsetFrames = ((track.offsetMs * targetSampleRate) / 1000L).toInt()
            val volume = track.volume

            // Stereo Pan calculation: pan in -1.0 .. +1.0
            // pan = -1.0 -> Left: 1.0, Right: 0.0
            // pan = 0.0 -> Left: 0.707, Right: 0.707
            // pan = +1.0 -> Left: 0.0, Right: 1.0
            val panL = ((1.0f - track.pan) / 2f).coerceIn(0f, 1f) * 1.414f * volume
            val panR = ((1.0f + track.pan) / 2f).coerceIn(0f, 1f) * 1.414f * volume

            val isTrackStereo = track.channels == 2
            val trackSampleCount = track.samples.size

            if (isTrackStereo) {
                val frames = trackSampleCount / 2
                for (f in 0 until frames) {
                    val outFrame = trackOffsetFrames + f
                    if (outFrame in 0 until totalStereoFrames) {
                        val inL = track.samples[f * 2].toFloat()
                        val inR = track.samples[f * 2 + 1].toFloat()
                        masterL[outFrame] += inL * panL
                        masterR[outFrame] += inR * panR
                    }
                }
            } else {
                for (s in 0 until trackSampleCount) {
                    val outFrame = trackOffsetFrames + s
                    if (outFrame in 0 until totalStereoFrames) {
                        val inSample = track.samples[s].toFloat()
                        masterL[outFrame] += inSample * panL
                        masterR[outFrame] += inSample * panR
                    }
                }
            }
        }

        // Interleave L and R with master soft limiter
        val interleaved = ShortArray(totalStereoFrames * 2)
        for (f in 0 until totalStereoFrames) {
            val lClamped = softLimit(masterL[f])
            val rClamped = softLimit(masterR[f])
            interleaved[f * 2] = lClamped.toInt().toShort()
            interleaved[f * 2 + 1] = rClamped.toInt().toShort()
        }

        return interleaved
    }

    private fun softLimit(sample: Float): Float {
        val norm = sample / 32768f
        val limited = if (kotlin.math.abs(norm) < 0.8f) {
            norm
        } else if (norm > 0) {
            0.8f + 0.2f * kotlin.math.tanh((norm - 0.8f) / 0.2f).toFloat()
        } else {
            -0.8f - 0.2f * kotlin.math.tanh((-norm - 0.8f) / 0.2f).toFloat()
        }
        return (limited * 32767f).coerceIn(-32768f, 32767f)
    }

    /**
     * Exports the mixed multi-track composition to a stereo WAV file.
     */
    fun exportMix(destinationFile: File, sampleRate: Int = 44100): Boolean {
        val mixedSamples = renderMix(sampleRate)
        if (mixedSamples.isEmpty()) return false
        return try {
            WavUtil.writePcmSamplesToWav(
                wavFile = destinationFile,
                samples = mixedSamples,
                sampleRate = sampleRate,
                channels = 2 // Stereo
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Real-time Multi-Track Playback in sync.
     */
    fun startPlayback(fromMs: Long = 0L) {
        stopPlayback()
        val mixedSamples = renderMix(44100)
        if (mixedSamples.isEmpty()) return

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = max(minBuf * 2, 8192)

        try {
            audioTrackPlayer = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrackPlayer?.play()
            _isPlaying.value = true

            val startFrame = ((fromMs * sampleRate) / 1000L).toInt() * 2 // stereo
            val actualStart = startFrame.coerceIn(0, mixedSamples.size)

            playbackJob = scope.launch(Dispatchers.IO) {
                var offset = actualStart
                val chunkSize = 2048
                val startTime = System.currentTimeMillis() - fromMs

                while (isActive && offset < mixedSamples.size && _isPlaying.value) {
                    val toWrite = minOf(chunkSize, mixedSamples.size - offset)
                    audioTrackPlayer?.write(mixedSamples, offset, toWrite)
                    offset += toWrite

                    val elapsedMs = System.currentTimeMillis() - startTime
                    _playbackPositionMs.value = elapsedMs.coerceAtMost(_totalDurationMs.value)
                }

                _isPlaying.value = false
                _playbackPositionMs.value = 0L
            }
        } catch (e: Exception) {
            _isPlaying.value = false
        }
    }

    fun stopPlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        try {
            audioTrackPlayer?.stop()
            audioTrackPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioTrackPlayer = null
    }

    fun seekTo(positionMs: Long) {
        val wasPlaying = _isPlaying.value
        if (wasPlaying) {
            startPlayback(positionMs)
        } else {
            _playbackPositionMs.value = positionMs.coerceIn(0L, _totalDurationMs.value)
        }
    }
}
