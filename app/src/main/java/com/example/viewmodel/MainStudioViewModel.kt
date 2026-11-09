package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.*
import com.example.audio.service.RecordingService
import com.example.data.local.StudioDatabase
import com.example.data.model.ProjectTrackEntity
import com.example.data.model.RecordingEntity
import com.example.data.model.RecordingNoteEntity
import com.example.data.model.StudioProjectEntity
import com.example.data.preferences.StudioPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class RecordingStudioMode(
    val id: String,
    val title: String,
    val description: String,
    val format: String,
    val isStereo: Boolean,
    val sampleRate: Int,
    val bitrate: String,
    val iconName: String
)

class MainStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val db = StudioDatabase.getInstance(application)
    private val recordingDao = db.recordingDao()
    private val projectDao = db.studioProjectDao()
    private val noteDao = db.recordingNoteDao()
    val preferences = StudioPreferences(application)

    val recordManager = AudioRecordManager(application)
    val playerManager = AudioPlayerManager(application)
    val editorEngine = AudioEditorEngine()
    val mixerEngine = MultiTrackMixerEngine()
    val metronome = StudioMetronome()

    // Studio Recording Modes
    val studioModes = listOf(
        RecordingStudioMode(
            id = "standard",
            title = "Standard",
            description = "Widely compatible, high-quality studio sound reproduction.",
            format = "WAV",
            isStereo = false,
            sampleRate = 44100,
            bitrate = "1411kbps",
            iconName = "mic"
        ),
        RecordingStudioMode(
            id = "music",
            title = "Music & raw sound",
            description = "Add music and sing along, perfect for vocal and acoustic recording.",
            format = "WAV",
            isStereo = true,
            sampleRate = 48000,
            bitrate = "1536kbps",
            iconName = "guitar"
        ),
        RecordingStudioMode(
            id = "meeting",
            title = "Meeting & Interview",
            description = "Omnidirectional vocal clarity with advanced background noise reduction.",
            format = "WAV",
            isStereo = false,
            sampleRate = 44100,
            bitrate = "1411kbps",
            iconName = "groups"
        ),
        RecordingStudioMode(
            id = "asmr",
            title = "ASMR & Whispers",
            description = "Ultra high-sensitivity dynamic capture for subtle acoustic textures.",
            format = "WAV",
            isStereo = true,
            sampleRate = 48000,
            bitrate = "1536kbps",
            iconName = "graphic_eq"
        )
    )

    private val _selectedMode = MutableStateFlow(studioModes[0])
    val selectedMode: StateFlow<RecordingStudioMode> = _selectedMode.asStateFlow()

    // Filter and search
    val selectedTag = MutableStateFlow("All")
    val searchQuery = MutableStateFlow("")

    val allRecordings = recordingDao.getAllRecordings().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val filteredRecordings: StateFlow<List<RecordingEntity>> = combine(
        allRecordings,
        selectedTag,
        searchQuery
    ) { recordings, tag, query ->
        recordings.filter { rec ->
            val matchesTag = if (tag == "All") true else rec.tag.equals(tag, ignoreCase = true)
            val matchesQuery = query.isEmpty() || rec.title.contains(query, ignoreCase = true)
            matchesTag && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Teleprompter / Notes
    val allNotes: StateFlow<List<RecordingNoteEntity>> = noteDao.getAllNotes().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _activeTeleprompterNote = MutableStateFlow<RecordingNoteEntity?>(null)
    val activeTeleprompterNote: StateFlow<RecordingNoteEntity?> = _activeTeleprompterNote.asStateFlow()

    val isTeleprompterOpen = MutableStateFlow(false)
    val teleprompterScrollSpeed = MutableStateFlow(2.0f)
    val teleprompterFontSizeSp = MutableStateFlow(20)
    val isAutoScrolling = MutableStateFlow(false)

    // Editor state
    private val _editingRecording = MutableStateFlow<RecordingEntity?>(null)
    val editingRecording: StateFlow<RecordingEntity?> = _editingRecording.asStateFlow()

    val editorStartMs = MutableStateFlow(0L)
    val editorEndMs = MutableStateFlow(0L)
    val editorCurrentDurationMs = MutableStateFlow(0L)
    val editorWaveformPoints = MutableStateFlow<List<Int>>(emptyList())
    val selectedEqPreset = MutableStateFlow(AudioEffectsProcessor.EQ_PRESETS[0])
    val selectedVoicePreset = MutableStateFlow(VoiceEffectPreset.NORMAL)

    // Mixer UI State
    val mixerTracksList = MutableStateFlow<List<MixerTrackData>>(emptyList())

    // App Preferences
    val themeMode = preferences.themeModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DARK")

    init {
        initDefaultNotes()
        initDefaultMixerSession()
    }

    fun selectMode(mode: RecordingStudioMode) {
        _selectedMode.value = mode
    }

    private fun initDefaultNotes() {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = noteDao.getAllNotes().first()
            if (existing.isEmpty()) {
                noteDao.insertNote(
                    RecordingNoteEntity(
                        title = "Acoustic Ballad Lyrics",
                        content = """[Verse 1]
Walking through the city in the neon light
Chasing every melody into the night
Strings keep ringing on an open chord
Words I never spoke, thoughts I never stored.

[Chorus]
Sing it loud, let the rhythm take the room
Clear sound echo like the morning in bloom
Turn up the gain, hear the harmony rise
Underneath the midnight skies.

[Bridge]
Bassline pulsing like a steady heartbeat
Studio acoustics sharp and complete.
""",
                        scrollSpeed = 2.0f,
                        fontSizeSp = 20
                    )
                )
                noteDao.insertNote(
                    RecordingNoteEntity(
                        title = "Podcast Episode Script: Future of Audio",
                        content = """[Intro - 00:00]
Welcome to the ProVoice Podcast, where sound design meets studio engineering.
Today we're exploring multi-track mixing, noise reduction algorithms, and acoustic clarity.

[Topic 1 - 00:45]
Why 48kHz 24-bit recording makes all the difference when mastering vocals...
Notice the dynamic response when using the presence boost filter.

[Outro - 02:00]
Thanks for tuning into ProVoice Studio. Don't forget to like, subscribe, and mix down your tracks!
""",
                        scrollSpeed = 2.5f,
                        fontSizeSp = 18
                    )
                )
            }
        }
    }

    private fun initDefaultMixerSession() {
        viewModelScope.launch(Dispatchers.IO) {
            // Generate presets if needed
            val track1 = StudioBackingTracks.getOrCreateTrackFile(
                getApplication(),
                StudioBackingTracks.PRESET_BACKING_TRACKS[0]
            )
            val track2 = StudioBackingTracks.getOrCreateTrackFile(
                getApplication(),
                StudioBackingTracks.PRESET_BACKING_TRACKS[1]
            )

            mixerEngine.loadTrack(
                id = 1,
                name = "Acoustic Guitar Chords",
                file = track1,
                volume = 0.85f,
                pan = -0.3f,
                colorHex = "#FF9800"
            )
            mixerEngine.loadTrack(
                id = 2,
                name = "Lo-Fi Hip-Hop Beat",
                file = track2,
                volume = 0.90f,
                pan = 0.2f,
                colorHex = "#9C27B0"
            )

            mixerTracksList.value = mixerEngine.tracks.toList()
        }
    }

    fun startRecording(titlePrefix: String? = null): Boolean {
        val mode = _selectedMode.value
        val prefix = titlePrefix ?: mode.title.replace(" ", "_")
        val success = recordManager.startRecording(
            sampleRate = mode.sampleRate,
            isStereo = mode.isStereo,
            gain = 1.0f,
            enableNoiseReduction = mode.id != "music",
            enableEchoCancellation = true,
            isHighSensitivity = mode.id == "asmr",
            titlePrefix = prefix
        )
        if (success) {
            RecordingService.startService(getApplication())
        }
        return success
    }

    fun pauseRecording() {
        recordManager.pauseRecording()
    }

    fun resumeRecording() {
        recordManager.resumeRecording()
    }

    fun addMarker(label: String? = null) {
        recordManager.addMarker(label)
    }

    fun stopAndSaveRecording(customTitle: String? = null, customTag: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val mode = _selectedMode.value
            val title = customTitle?.takeIf { it.isNotBlank() } ?: "${mode.title}_${System.currentTimeMillis() % 10000}"
            val tag = customTag ?: when (mode.id) {
                "music" -> "Music"
                "meeting" -> "Meeting"
                "asmr" -> "Voice"
                else -> "Standard"
            }

            val wavFile = recordManager.stopAndSaveRecording(title, tag)
            RecordingService.stopService(getApplication())

            if (wavFile != null && wavFile.exists()) {
                val info = WavUtil.parseWavHeader(wavFile)
                val points = WavUtil.extractWaveformPoints(wavFile, 80)
                val pointsStr = points.joinToString(",")

                val entity = RecordingEntity(
                    title = title,
                    filePath = wavFile.absolutePath,
                    format = "WAV",
                    durationMs = info?.durationMs ?: 0L,
                    fileSize = wavFile.length(),
                    sampleRate = info?.sampleRate ?: mode.sampleRate,
                    channels = info?.channels ?: (if (mode.isStereo) 2 else 1),
                    tag = tag,
                    waveformPoints = pointsStr,
                    notes = _activeTeleprompterNote.value?.content ?: ""
                )
                recordingDao.insertRecording(entity)
            }
        }
    }

    fun cancelRecording() {
        recordManager.cancelRecording()
        RecordingService.stopService(getApplication())
    }

    // Teleprompter methods
    fun selectTeleprompterNote(note: RecordingNoteEntity?) {
        _activeTeleprompterNote.value = note
        note?.let {
            teleprompterScrollSpeed.value = it.scrollSpeed
            teleprompterFontSizeSp.value = it.fontSizeSp
        }
    }

    fun saveTeleprompterNote(title: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _activeTeleprompterNote.value
            if (current != null) {
                val updated = current.copy(
                    title = title,
                    content = content,
                    scrollSpeed = teleprompterScrollSpeed.value,
                    fontSizeSp = teleprompterFontSizeSp.value,
                    updatedAt = System.currentTimeMillis()
                )
                noteDao.updateNote(updated)
                _activeTeleprompterNote.value = updated
            } else {
                val newNote = RecordingNoteEntity(
                    title = title.ifBlank { "Studio Script" },
                    content = content,
                    scrollSpeed = teleprompterScrollSpeed.value,
                    fontSizeSp = teleprompterFontSizeSp.value
                )
                val id = noteDao.insertNote(newNote)
                _activeTeleprompterNote.value = newNote.copy(id = id)
            }
        }
    }

    fun deleteTeleprompterNote(note: RecordingNoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            noteDao.deleteNote(note)
            if (_activeTeleprompterNote.value?.id == note.id) {
                _activeTeleprompterNote.value = null
            }
        }
    }

    // Library management
    fun playRecording(recording: RecordingEntity) {
        val file = File(recording.filePath)
        playerManager.playFile(file, recording.title)
    }

    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            recordingDao.deleteRecording(recording)
            try {
                File(recording.filePath).delete()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun renameRecording(recording: RecordingEntity, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            recordingDao.updateRecording(recording.copy(title = newTitle))
        }
    }

    fun toggleFavorite(recording: RecordingEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            recordingDao.updateRecording(recording.copy(isFavorite = !recording.isFavorite))
        }
    }

    // Audio Editor Operations
    fun openRecordingInEditor(recording: RecordingEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(recording.filePath)
            if (editorEngine.loadWavFile(file)) {
                _editingRecording.value = recording
                editorStartMs.value = 0L
                editorEndMs.value = editorEngine.getDurationMs()
                editorCurrentDurationMs.value = editorEngine.getDurationMs()
                val points = WavUtil.extractWaveformPoints(file, 100)
                editorWaveformPoints.value = points
            }
        }
    }

    fun editorTrim() {
        if (editorEngine.trim(editorStartMs.value, editorEndMs.value)) {
            refreshEditorState()
        }
    }

    fun editorCut() {
        if (editorEngine.cut(editorStartMs.value, editorEndMs.value)) {
            refreshEditorState()
        }
    }

    fun editorCopy() {
        editorEngine.copy(editorStartMs.value, editorEndMs.value)
    }

    fun editorPaste() {
        if (editorEngine.paste(editorStartMs.value)) {
            refreshEditorState()
        }
    }

    fun editorFadeIn() {
        if (editorEngine.fadeIn(1200L)) {
            refreshEditorState()
        }
    }

    fun editorFadeOut() {
        if (editorEngine.fadeOut(1200L)) {
            refreshEditorState()
        }
    }

    fun editorChangeSpeed(speed: Float) {
        if (editorEngine.changeSpeed(speed)) {
            refreshEditorState()
        }
    }

    fun editorApplyEq(preset: EqPreset) {
        selectedEqPreset.value = preset
        if (editorEngine.applyEqualizer(preset.band1, preset.band2, preset.band3, preset.band4, preset.band5)) {
            refreshEditorState()
        }
    }

    fun editorApplyVoicePreset(preset: VoiceEffectPreset) {
        selectedVoicePreset.value = preset
        if (editorEngine.applyVoicePreset(preset)) {
            refreshEditorState()
        }
    }

    fun editorNormalize() {
        if (editorEngine.normalize()) {
            refreshEditorState()
        }
    }

    fun editorTrimSilence() {
        if (editorEngine.trimSilence()) {
            refreshEditorState()
        }
    }

    fun editorAutoTune() {
        if (editorEngine.applyAutoTune()) {
            refreshEditorState()
        }
    }

    fun editorUndo() {
        if (editorEngine.undo()) {
            refreshEditorState()
        }
    }

    fun editorRedo() {
        if (editorEngine.redo()) {
            refreshEditorState()
        }
    }

    private fun refreshEditorState() {
        editorCurrentDurationMs.value = editorEngine.getDurationMs()
        editorStartMs.value = 0L
        editorEndMs.value = editorEngine.getDurationMs()

        // Recompute waveform points from current samples
        val samples = editorEngine.currentSamples
        if (samples.isNotEmpty()) {
            val pointCount = 100
            val step = maxOf(1, samples.size / pointCount)
            val points = mutableListOf<Int>()
            for (i in 0 until pointCount) {
                val start = i * step
                val end = minOf(samples.size, start + step)
                var maxChunk = 0
                for (j in start until end) {
                    val a = kotlin.math.abs(samples[j].toInt())
                    if (a > maxChunk) maxChunk = a
                }
                val norm = ((maxChunk.toFloat() / 32768f) * 90f + 10f).toInt().coerceIn(10, 100)
                points.add(norm)
            }
            editorWaveformPoints.value = points
        }
    }

    fun saveEditedAudio(titleSuffix: String = "_edited") {
        viewModelScope.launch(Dispatchers.IO) {
            val original = _editingRecording.value ?: return@launch
            val editDir = File(getApplication<Application>().filesDir, "recordings").apply { mkdirs() }
            val newFile = File(editDir, "${original.title}${titleSuffix}_${System.currentTimeMillis()}.wav")
            if (editorEngine.exportToWav(newFile)) {
                val info = WavUtil.parseWavHeader(newFile)
                val points = WavUtil.extractWaveformPoints(newFile, 80)
                val newEntity = RecordingEntity(
                    title = "${original.title}${titleSuffix}",
                    filePath = newFile.absolutePath,
                    format = "WAV",
                    durationMs = info?.durationMs ?: editorEngine.getDurationMs(),
                    fileSize = newFile.length(),
                    sampleRate = editorEngine.sampleRate,
                    channels = editorEngine.channels,
                    tag = original.tag,
                    waveformPoints = points.joinToString(",")
                )
                recordingDao.insertRecording(newEntity)
            }
        }
    }

    // Multi-track Mixer controls
    fun loadRecordingIntoMixerTrack(trackId: Long, recording: RecordingEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            mixerEngine.loadTrack(
                id = trackId,
                name = recording.title,
                file = File(recording.filePath),
                colorHex = when ((trackId % 4).toInt()) {
                    0 -> "#FF5722"
                    1 -> "#00E5FF"
                    2 -> "#FFC107"
                    else -> "#9C27B0"
                }
            )
            mixerTracksList.value = mixerEngine.tracks.toList()
        }
    }

    fun loadPresetBackingTrackIntoMixer(trackId: Long, presetInfo: StudioBackingTracks.BackingTrackInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = StudioBackingTracks.getOrCreateTrackFile(getApplication(), presetInfo)
            mixerEngine.loadTrack(
                id = trackId,
                name = presetInfo.name,
                file = file,
                colorHex = presetInfo.colorHex
            )
            mixerTracksList.value = mixerEngine.tracks.toList()
        }
    }

    fun updateMixerTrackVolume(trackId: Long, volume: Float) {
        mixerEngine.updateTrackVolume(trackId, volume)
        mixerTracksList.value = mixerEngine.tracks.toList()
    }

    fun updateMixerTrackPan(trackId: Long, pan: Float) {
        mixerEngine.updateTrackPan(trackId, pan)
        mixerTracksList.value = mixerEngine.tracks.toList()
    }

    fun toggleMixerTrackMute(trackId: Long) {
        mixerEngine.toggleTrackMute(trackId)
        mixerTracksList.value = mixerEngine.tracks.toList()
    }

    fun toggleMixerTrackSolo(trackId: Long) {
        mixerEngine.toggleTrackSolo(trackId)
        mixerTracksList.value = mixerEngine.tracks.toList()
    }

    fun updateMixerTrackOffset(trackId: Long, offsetMs: Long) {
        mixerEngine.updateTrackOffset(trackId, offsetMs)
        mixerTracksList.value = mixerEngine.tracks.toList()
    }

    fun removeMixerTrack(trackId: Long) {
        mixerEngine.removeTrack(trackId)
        mixerTracksList.value = mixerEngine.tracks.toList()
    }

    fun exportMixdown(title: String = "Studio_Mixdown") {
        viewModelScope.launch(Dispatchers.IO) {
            val recordDir = File(getApplication<Application>().filesDir, "recordings").apply { mkdirs() }
            val destination = File(recordDir, "${title}_${System.currentTimeMillis()}.wav")
            if (mixerEngine.exportMix(destination)) {
                val info = WavUtil.parseWavHeader(destination)
                val points = WavUtil.extractWaveformPoints(destination, 80)
                val newRec = RecordingEntity(
                    title = title,
                    filePath = destination.absolutePath,
                    format = "WAV",
                    durationMs = info?.durationMs ?: 0L,
                    fileSize = destination.length(),
                    sampleRate = 44100,
                    channels = 2,
                    tag = "Music",
                    waveformPoints = points.joinToString(",")
                )
                recordingDao.insertRecording(newRec)
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
        }
    }

    fun setSampleRate(sr: Int) {
        viewModelScope.launch {
            preferences.setSampleRate(sr)
        }
    }

    fun setAudioFormat(fmt: String) {
        viewModelScope.launch {
            preferences.setAudioFormat(fmt)
        }
    }

    fun setMicGain(gain: Float) {
        viewModelScope.launch {
            preferences.setMicGain(gain)
            recordManager.updateGain(gain)
        }
    }

    fun setNoiseReduction(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setNoiseReduction(enabled)
        }
    }

    override fun onCleared() {
        super.onCleared()
        recordManager.cancelRecording()
        playerManager.stop()
        mixerEngine.stopPlayback()
    }
}
