package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "provoice_settings")

class StudioPreferences(private val context: Context) {

    companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode") // SYSTEM, LIGHT, DARK
        val KEY_FORMAT = stringPreferencesKey("audio_format") // WAV, M4A
        val KEY_SAMPLE_RATE = intPreferencesKey("sample_rate") // 44100, 48000
        val KEY_MIC_GAIN = floatPreferencesKey("mic_gain") // 0.5f..2.0f
        val KEY_NOISE_REDUCTION = booleanPreferencesKey("noise_reduction")
        val KEY_ECHO_CANCELLATION = booleanPreferencesKey("echo_cancellation")
        val KEY_HIGH_SENSITIVITY = booleanPreferencesKey("high_sensitivity")
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "DARK" }
    val formatFlow: Flow<String> = context.dataStore.data.map { it[KEY_FORMAT] ?: "WAV" }
    val sampleRateFlow: Flow<Int> = context.dataStore.data.map { it[KEY_SAMPLE_RATE] ?: 44100 }
    val micGainFlow: Flow<Float> = context.dataStore.data.map { it[KEY_MIC_GAIN] ?: 1.0f }
    val noiseReductionFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOISE_REDUCTION] ?: true }
    val echoCancellationFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_ECHO_CANCELLATION] ?: true }
    val highSensitivityFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_HIGH_SENSITIVITY] ?: false }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME] = mode }
    }

    suspend fun setAudioFormat(format: String) {
        context.dataStore.edit { it[KEY_FORMAT] = format }
    }

    suspend fun setSampleRate(sampleRate: Int) {
        context.dataStore.edit { it[KEY_SAMPLE_RATE] = sampleRate }
    }

    suspend fun setMicGain(gain: Float) {
        context.dataStore.edit { it[KEY_MIC_GAIN] = gain }
    }

    suspend fun setNoiseReduction(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOISE_REDUCTION] = enabled }
    }

    suspend fun setEchoCancellation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ECHO_CANCELLATION] = enabled }
    }

    suspend fun setHighSensitivity(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HIGH_SENSITIVITY] = enabled }
    }
}
