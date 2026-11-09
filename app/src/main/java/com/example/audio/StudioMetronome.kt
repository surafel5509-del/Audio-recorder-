package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class StudioMetronome {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var metronomeJob: Job? = null
    private var audioTrack: AudioTrack? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _bpm = MutableStateFlow(120)
    val bpm: StateFlow<Int> = _bpm.asStateFlow()

    private val _currentBeat = MutableStateFlow(1)
    val currentBeat: StateFlow<Int> = _currentBeat.asStateFlow()

    private val _beatsPerBar = MutableStateFlow(4)
    val beatsPerBar: StateFlow<Int> = _beatsPerBar.asStateFlow()

    private val highClick: ShortArray
    private val lowClick: ShortArray

    init {
        val sampleRate = 44100
        val clickLength = (sampleRate * 0.04).toInt() // 40ms click
        highClick = ShortArray(clickLength)
        lowClick = ShortArray(clickLength)

        // Synthesize woodblock / digital click
        for (i in 0 until clickLength) {
            val env = exp(-i.toFloat() / (sampleRate * 0.008f))
            val highWave = sin(2.0 * PI * 2200.0 * i / sampleRate).toFloat()
            val lowWave = sin(2.0 * PI * 1200.0 * i / sampleRate).toFloat()

            highClick[i] = (highWave * env * 22000f).toInt().toShort()
            lowClick[i] = (lowWave * env * 18000f).toInt().toShort()
        }
    }

    fun setBpm(newBpm: Int) {
        _bpm.value = newBpm.coerceIn(40, 240)
    }

    fun setBeatsPerBar(beats: Int) {
        _beatsPerBar.value = beats.coerceIn(2, 8)
    }

    fun toggle() {
        if (_isPlaying.value) stop() else start()
    }

    fun start() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        _currentBeat.value = 1

        val sampleRate = 44100
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(4096)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        metronomeJob = scope.launch {
            var beat = 1
            while (isActive && _isPlaying.value) {
                val currentBpm = _bpm.value
                val intervalMs = (60_000L / currentBpm)
                val clickToPlay = if (beat == 1) highClick else lowClick

                audioTrack?.write(clickToPlay, 0, clickToPlay.size)
                _currentBeat.value = beat

                beat = if (beat >= _beatsPerBar.value) 1 else beat + 1
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        _isPlaying.value = false
        metronomeJob?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioTrack = null
        _currentBeat.value = 1
    }
}
