package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioEffectsProcessor
import com.example.audio.VoiceEffectPreset
import com.example.audio.WavUtil
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ProVoice Studio", appName)
    }

    @Test
    fun testWavGenerationAndParsing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testFile = File(context.cacheDir, "unit_test.wav")
        val sampleRate = 44100
        val testSamples = ShortArray(sampleRate) { (it % 1000).toShort() }

        WavUtil.writePcmSamplesToWav(
            wavFile = testFile,
            samples = testSamples,
            sampleRate = sampleRate,
            channels = 1
        )

        assertTrue(testFile.exists())
        val info = WavUtil.parseWavHeader(testFile)
        assertNotNull(info)
        assertEquals(sampleRate, info?.sampleRate)
        assertEquals(1, info?.channels)
        assertEquals(1000L, info?.durationMs)

        val readBack = WavUtil.readPcmSamples(testFile)
        assertEquals(testSamples.size, readBack.size)

        val waveform = WavUtil.extractWaveformPoints(testFile, 50)
        assertEquals(50, waveform.size)
        assertTrue(waveform.all { it in 8..100 })
    }

    @Test
    fun testAudioEffectsProcessing() {
        val sampleRate = 44100
        val testSamples = ShortArray(1000) { 1500 }

        // Test EQ
        val eqOutput = AudioEffectsProcessor.applyEqualizer(
            samples = testSamples,
            sampleRate = sampleRate,
            band1GainDb = 3f,
            band2GainDb = 0f,
            band3GainDb = -2f,
            band4GainDb = 4f,
            band5GainDb = 1f
        )
        assertEquals(testSamples.size, eqOutput.size)

        // Test Voice Changer
        val robotOutput = AudioEffectsProcessor.applyVoiceEffect(
            samples = testSamples,
            sampleRate = sampleRate,
            preset = VoiceEffectPreset.ROBOT
        )
        assertEquals(testSamples.size, robotOutput.size)

        // Test Compressor
        val compOutput = AudioEffectsProcessor.applyCompressor(
            samples = testSamples,
            thresholdDb = -12f,
            ratio = 4f
        )
        assertEquals(testSamples.size, compOutput.size)
    }
}
