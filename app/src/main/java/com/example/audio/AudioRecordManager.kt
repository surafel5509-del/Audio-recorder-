package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

enum class RecordingState {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED
}

data class RecordingMarker(
    val id: Long = System.currentTimeMillis(),
    val timestampMs: Long,
    val label: String
)

class AudioRecordManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var timerJob: Job? = null

    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var autoGainControl: AutomaticGainControl? = null

    private var pcmTempFile: File? = null
    private var currentWavFile: File? = null

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _currentDb = MutableStateFlow(0f)
    val currentDb: StateFlow<Float> = _currentDb.asStateFlow()

    private val _leftDb = MutableStateFlow(0f)
    val leftDb: StateFlow<Float> = _leftDb.asStateFlow()

    private val _rightDb = MutableStateFlow(0f)
    val rightDb: StateFlow<Float> = _rightDb.asStateFlow()

    private val _isClipping = MutableStateFlow(false)
    val isClipping: StateFlow<Boolean> = _isClipping.asStateFlow()

    private val _livePitch = MutableStateFlow<PitchInfo?>(null)
    val livePitch: StateFlow<PitchInfo?> = _livePitch.asStateFlow()

    private val _countdown = MutableStateFlow(0)
    val countdown: StateFlow<Int> = _countdown.asStateFlow()

    // 60 recent amplitude bars for live scrolling waveform visualizer
    private val _liveWaveform = MutableStateFlow<List<Float>>(List(60) { 5f })
    val liveWaveform: StateFlow<List<Float>> = _liveWaveform.asStateFlow()

    private val _markers = MutableStateFlow<List<RecordingMarker>>(emptyList())
    val markers: StateFlow<List<RecordingMarker>> = _markers.asStateFlow()

    private var sampleRate = 44100
    private var channelConfig = AudioFormat.CHANNEL_IN_MONO
    private var audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var channels = 1
    private var gainMultiplier = 1.0f
    private var highSensitivity = false

    @SuppressLint("MissingPermission")
    fun startRecording(
        sampleRate: Int = 44100,
        isStereo: Boolean = false,
        gain: Float = 1.0f,
        enableNoiseReduction: Boolean = true,
        enableEchoCancellation: Boolean = true,
        isHighSensitivity: Boolean = false,
        titlePrefix: String = "Recording"
    ): Boolean {
        if (_recordingState.value == RecordingState.RECORDING) return false

        this.sampleRate = sampleRate
        this.channels = if (isStereo) 2 else 1
        this.channelConfig = if (isStereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        this.gainMultiplier = gain
        this.highSensitivity = isHighSensitivity

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize * 2, 4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioRecordManager", "AudioRecord initialization failed")
                return false
            }

            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                if (enableNoiseReduction && NoiseSuppressor.isAvailable()) {
                    try {
                        noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                    } catch (e: Exception) {
                        Log.w("AudioRecordManager", "NoiseSuppressor failed: ${e.message}")
                    }
                }
                if (enableEchoCancellation && AcousticEchoCanceler.isAvailable()) {
                    try {
                        echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                    } catch (e: Exception) {
                        Log.w("AudioRecordManager", "EchoCanceler failed: ${e.message}")
                    }
                }
                if (AutomaticGainControl.isAvailable() && !isHighSensitivity) {
                    try {
                        autoGainControl = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
                    } catch (e: Exception) {
                        Log.w("AudioRecordManager", "AGC failed: ${e.message}")
                    }
                }
            }

            // Create temp PCM file
            val recordDir = File(context.filesDir, "recordings").apply { mkdirs() }
            pcmTempFile = File(recordDir, "temp_rec_${System.currentTimeMillis()}.pcm")
            currentWavFile = File(recordDir, "${titlePrefix}_${System.currentTimeMillis()}.wav")

            audioRecord?.startRecording()
            _recordingState.value = RecordingState.RECORDING
            _recordingDurationMs.value = 0L
            _markers.value = emptyList()
            _liveWaveform.value = List(60) { 5f }

            startRecordingLoop(bufferSize)
            startTimer()
            return true
        } catch (e: Exception) {
            Log.e("AudioRecordManager", "Failed to start recording", e)
            cleanUp()
            return false
        }
    }

    private fun startRecordingLoop(bufferSize: Int) {
        recordingJob = scope.launch(Dispatchers.IO) {
            val audioBuffer = ShortArray(bufferSize / 2)
            val byteBuffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN)
            val pcmOut = FileOutputStream(pcmTempFile, true)

            try {
                while (isActive && (_recordingState.value == RecordingState.RECORDING || _recordingState.value == RecordingState.PAUSED)) {
                    if (_recordingState.value == RecordingState.PAUSED) {
                        delay(50)
                        continue
                    }

                    val readShorts = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                    if (readShorts > 0) {
                        byteBuffer.clear()
                        var sumSquares = 0.0
                        var maxAbs = 0

                        for (i in 0 until readShorts) {
                            var sample = audioBuffer[i].toInt()
                            // Apply software gain
                            sample = (sample * gainMultiplier).toInt()
                            if (highSensitivity) {
                                // Dynamic soft boost for ASMR
                                val sign = if (sample >= 0) 1 else -1
                                val absVal = abs(sample).toDouble()
                                val boosted = (sqrt(absVal / 32767.0) * 32767.0).toInt()
                                sample = sign * boosted
                            }
                            val clamped = sample.coerceIn(-32768, 32767).toShort()
                            byteBuffer.putShort(clamped)

                            val absShort = abs(clamped.toInt())
                            if (absShort > maxAbs) maxAbs = absShort
                            sumSquares += clamped * clamped
                        }

                        pcmOut.write(byteBuffer.array(), 0, readShorts * 2)

                        // Calculate RMS & dB
                        val rms = sqrt(sumSquares / readShorts)
                        val db = if (rms > 0) {
                            (20 * log10(rms / 32767.0)).toFloat().coerceIn(-60f, 0f)
                        } else {
                            -60f
                        }
                        // Normalize dB to 0..100
                        val normalizedDb = ((db + 60f) / 60f * 100f).coerceIn(0f, 100f)
                        _currentDb.value = normalizedDb
                        _leftDb.value = normalizedDb
                        _rightDb.value = (normalizedDb * (if (channels == 2) 0.95f else 1.0f)).coerceIn(0f, 100f)
                        _isClipping.value = maxAbs >= 32600

                        // Detect vocal pitch for singer tuner
                        if (rms > 400.0) {
                            val pitch = AudioPitchDetector.detectPitch(audioBuffer, sampleRate)
                            _livePitch.value = pitch
                        } else {
                            _livePitch.value = null
                        }

                        // Update waveform points
                        val ampPercent = (maxAbs.toFloat() / 32767f * 100f).coerceIn(4f, 100f)
                        val currentList = _liveWaveform.value.toMutableList()
                        if (currentList.size >= 60) {
                            currentList.removeAt(0)
                        }
                        currentList.add(ampPercent)
                        _liveWaveform.value = currentList
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioRecordManager", "Error in recording loop", e)
            } finally {
                try {
                    pcmOut.flush()
                    pcmOut.close()
                } catch (e: Exception) {
                    Log.e("AudioRecordManager", "Error closing PCM stream", e)
                }
            }
        }
    }

    private fun startTimer() {
        timerJob = scope.launch(Dispatchers.Default) {
            while (isActive && (_recordingState.value == RecordingState.RECORDING || _recordingState.value == RecordingState.PAUSED)) {
                delay(100)
                if (_recordingState.value == RecordingState.RECORDING) {
                    _recordingDurationMs.value += 100
                }
            }
        }
    }

    fun pauseRecording() {
        if (_recordingState.value == RecordingState.RECORDING) {
            _recordingState.value = RecordingState.PAUSED
        }
    }

    fun resumeRecording() {
        if (_recordingState.value == RecordingState.PAUSED) {
            _recordingState.value = RecordingState.RECORDING
        }
    }

    fun addMarker(customLabel: String? = null) {
        val currentMs = _recordingDurationMs.value
        val label = customLabel ?: "Marker ${_markers.value.size + 1}"
        val newMarker = RecordingMarker(
            timestampMs = currentMs,
            label = label
        )
        _markers.value = _markers.value + newMarker
    }

    suspend fun stopAndSaveRecording(title: String, tag: String = "Untagged"): File? = withContext(Dispatchers.IO) {
        if (_recordingState.value == RecordingState.IDLE) return@withContext null

        _recordingState.value = RecordingState.STOPPED
        recordingJob?.cancelAndJoin()
        timerJob?.cancelAndJoin()

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e("AudioRecordManager", "Error stopping AudioRecord", e)
        }

        cleanUpEffects()

        val pcm = pcmTempFile
        val wav = currentWavFile
        if (pcm != null && pcm.exists() && wav != null) {
            try {
                WavUtil.convertPcmToWav(
                    pcmFile = pcm,
                    wavFile = wav,
                    sampleRate = sampleRate,
                    channels = channels
                )
                pcm.delete()
                _recordingState.value = RecordingState.IDLE
                return@withContext wav
            } catch (e: Exception) {
                Log.e("AudioRecordManager", "Failed to convert PCM to WAV", e)
            }
        }

        _recordingState.value = RecordingState.IDLE
        return@withContext null
    }

    fun cancelRecording() {
        _recordingState.value = RecordingState.IDLE
        recordingJob?.cancel()
        timerJob?.cancel()
        cleanUp()
        pcmTempFile?.delete()
        currentWavFile?.delete()
        _recordingDurationMs.value = 0L
        _markers.value = emptyList()
        _liveWaveform.value = List(60) { 5f }
    }

    fun updateGain(gain: Float) {
        this.gainMultiplier = gain
    }

    private fun cleanUpEffects() {
        try {
            noiseSuppressor?.release()
            echoCanceler?.release()
            autoGainControl?.release()
        } catch (e: Exception) {
            // Ignore
        }
        noiseSuppressor = null
        echoCanceler = null
        autoGainControl = null
    }

    private fun cleanUp() {
        cleanUpEffects()
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioRecord = null
    }
}
