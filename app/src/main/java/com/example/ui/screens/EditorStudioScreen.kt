package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioEffectsProcessor
import com.example.audio.EqPreset
import com.example.audio.VoiceEffectPreset
import com.example.data.model.RecordingEntity
import com.example.ui.components.EqCurveGraph
import com.example.ui.components.StaticWaveformCanvas
import com.example.ui.components.formatTime
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioOrange
import com.example.viewmodel.MainStudioViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorStudioScreen(
    viewModel: MainStudioViewModel,
    recording: RecordingEntity,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editorEngine = viewModel.editorEngine
    val playerManager = viewModel.playerManager

    val startMs by viewModel.editorStartMs.collectAsState()
    val endMs by viewModel.editorEndMs.collectAsState()
    val totalMs by viewModel.editorCurrentDurationMs.collectAsState()
    val waveformPoints by viewModel.editorWaveformPoints.collectAsState()
    val selectedEqPreset by viewModel.selectedEqPreset.collectAsState()
    val selectedVoicePreset by viewModel.selectedVoicePreset.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var exportSuffix by remember { mutableStateOf("_mastered") }
    var activeEffectTab by remember { mutableStateOf("EQ") } // EQ, Voice, Dynamics

    LaunchedEffect(recording.id) {
        viewModel.openRecordingInEditor(recording)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(recording.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            text = "${formatTime(totalMs)} • ${recording.format} ${recording.sampleRate}Hz",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Undo Button
                    IconButton(
                        onClick = { viewModel.editorUndo() },
                        enabled = editorEngine.canUndo()
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = "Undo",
                            tint = if (editorEngine.canUndo()) StudioCyan else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }

                    // Redo Button
                    IconButton(
                        onClick = { viewModel.editorRedo() },
                        enabled = editorEngine.canRedo()
                    ) {
                        Icon(
                            Icons.Default.Redo,
                            contentDescription = "Redo",
                            tint = if (editorEngine.canRedo()) StudioCyan else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }

                    // Save / Export button
                    IconButton(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save Edited Audio", tint = StudioOrange)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Visual Waveform Editor Canvas & Selection
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("WAVEFORM TIMELINE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = StudioOrange)
                            Text(
                                text = "Selection: ${formatTime(startMs)} - ${formatTime(endMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = StudioCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Waveform Display
                        StaticWaveformCanvas(
                            waveformPoints = waveformPoints,
                            progress = if (totalMs > 0) (startMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f,
                            playedColor = StudioCyan,
                            unplayedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Selection Range Sliders
                        Text("Start Marker (${formatTime(startMs)})", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = if (totalMs > 0) (startMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f,
                            onValueChange = { ratio ->
                                val target = (ratio * totalMs).toLong()
                                viewModel.editorStartMs.value = minOf(target, endMs)
                            },
                            colors = SliderDefaults.colors(thumbColor = StudioCyan, activeTrackColor = StudioCyan)
                        )

                        Text("End Marker (${formatTime(endMs)})", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = if (totalMs > 0) (endMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 1f,
                            onValueChange = { ratio ->
                                val target = (ratio * totalMs).toLong()
                                viewModel.editorEndMs.value = maxOf(target, startMs)
                            },
                            colors = SliderDefaults.colors(thumbColor = StudioOrange, activeTrackColor = StudioOrange)
                        )
                    }
                }
            }

            // 2. Editing Action Toolbar: Trim, Cut, Copy, Paste, Fade In/Out
            item {
                Text(
                    text = "EDIT TOOLS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.editorTrim() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Trim", fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = { viewModel.editorCut() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cut", fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = { viewModel.editorCopy() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy", fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = { viewModel.editorPaste() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Paste", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.editorFadeIn() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Fade In (1.2s)", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { viewModel.editorFadeOut() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Fade Out (1.2s)", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Studio Mastering Tools (Normalize, Silence Trim, Auto-Tune)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.editorNormalize() },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = StudioCyan.copy(alpha = 0.15f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Equalizer, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Normalize", fontSize = 11.sp, color = StudioCyan, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { viewModel.editorTrimSilence() },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = StudioAmber.copy(alpha = 0.15f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, tint = StudioAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Trim Silence", fontSize = 11.sp, color = StudioAmber, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { viewModel.editorAutoTune() },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = StudioOrange.copy(alpha = 0.15f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = StudioOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto-Tune", fontSize = 11.sp, color = StudioOrange, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 3. Audio Speed Controls
            item {
                Text(
                    text = "PLAYBACK SPEED",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.editorChangeSpeed(speed) },
                            label = { Text("${speed}x", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. Studio Audio Effects Rack (Tabs: EQ, Voice Changer, Reverb/Dynamics)
            item {
                Text(
                    text = "STUDIO EFFECTS RACK",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                TabRow(
                    selectedTabIndex = when (activeEffectTab) {
                        "EQ" -> 0
                        "Voice" -> 1
                        else -> 2
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Tab(
                        selected = activeEffectTab == "EQ",
                        onClick = { activeEffectTab = "EQ" },
                        text = { Text("5-Band EQ") }
                    )
                    Tab(
                        selected = activeEffectTab == "Voice",
                        onClick = { activeEffectTab = "Voice" },
                        text = { Text("Voice Changer") }
                    )
                    Tab(
                        selected = activeEffectTab == "Dynamics",
                        onClick = { activeEffectTab = "Dynamics" },
                        text = { Text("Compressor & Reverb") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (activeEffectTab) {
                    "EQ" -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Graphic EQ Curve", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))

                                // Visual EQ frequency curve
                                EqCurveGraph(
                                    b1 = selectedEqPreset.band1,
                                    b2 = selectedEqPreset.band2,
                                    b3 = selectedEqPreset.band3,
                                    b4 = selectedEqPreset.band4,
                                    b5 = selectedEqPreset.band5,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                                        .padding(8.dp)
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Text("EQ Presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(AudioEffectsProcessor.EQ_PRESETS) { preset ->
                                        FilterChip(
                                            selected = selectedEqPreset.name == preset.name,
                                            onClick = { viewModel.editorApplyEq(preset) },
                                            label = { Text(preset.name) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = StudioOrange,
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Current Bands: 100Hz (${selectedEqPreset.band1}dB) • 400Hz (${selectedEqPreset.band2}dB) • 1kHz (${selectedEqPreset.band3}dB) • 4kHz (${selectedEqPreset.band4}dB) • 12kHz (${selectedEqPreset.band5}dB)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    "Voice" -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Voice Changer & Enhancer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(10.dp))

                                val presets = listOf(
                                    VoiceEffectPreset.NORMAL to "Normal",
                                    VoiceEffectPreset.STUDIO_VOCAL to "Studio Vocal Enhancer",
                                    VoiceEffectPreset.DEEP_VOICE to "Deep Voice",
                                    VoiceEffectPreset.CHIPMUNK to "Chipmunk",
                                    VoiceEffectPreset.ROBOT to "Robot Voice",
                                    VoiceEffectPreset.RADIO_MEGAPHONE to "Radio / Megaphone",
                                    VoiceEffectPreset.ECHO_CHAMBER to "Echo Chamber"
                                )

                                presets.forEach { (preset, label) ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selectedVoicePreset == preset) StudioCyan.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (selectedVoicePreset == preset) StudioCyan else Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable { viewModel.editorApplyVoicePreset(preset) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = when (preset) {
                                                    VoiceEffectPreset.STUDIO_VOCAL -> Icons.Default.AutoAwesome
                                                    VoiceEffectPreset.ROBOT -> Icons.Default.SmartToy
                                                    VoiceEffectPreset.RADIO_MEGAPHONE -> Icons.Default.Radio
                                                    VoiceEffectPreset.ECHO_CHAMBER -> Icons.Default.SurroundSound
                                                    else -> Icons.Default.RecordVoiceOver
                                                },
                                                contentDescription = null,
                                                tint = if (selectedVoicePreset == preset) StudioCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Compressor & Studio Reverb", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        viewModel.editorApplyVoicePreset(VoiceEffectPreset.STUDIO_VOCAL)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioOrange),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Compress, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Apply Studio Compressor & Limiter")
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        viewModel.editorApplyVoicePreset(VoiceEffectPreset.ECHO_CHAMBER)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.SurroundSound, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Apply Reverb & Echo Chamber", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }

        // Save Edited Dialog
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("Save Processed Audio", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Export this edited audio to your library:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = exportSuffix,
                            onValueChange = { exportSuffix = it },
                            label = { Text("Title Suffix") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveEditedAudio(exportSuffix)
                            showSaveDialog = false
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioOrange)
                    ) {
                        Text("Save Take")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}
