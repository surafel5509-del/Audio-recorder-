package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.RecordingMarker
import com.example.audio.RecordingState
import com.example.ui.components.LiveWaveformCanvas
import com.example.ui.components.PitchTunerDisplay
import com.example.ui.components.StereoDualVuMeter
import com.example.ui.components.TeleprompterOverlay
import com.example.ui.components.VuMeterBar
import com.example.ui.components.formatTime
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioOrange
import com.example.viewmodel.MainStudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordStudioScreen(
    viewModel: MainStudioViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recordManager = viewModel.recordManager

    val recordingState by recordManager.recordingState.collectAsState()
    val recordingDurationMs by recordManager.recordingDurationMs.collectAsState()
    val currentDb by recordManager.currentDb.collectAsState()
    val leftDb by recordManager.leftDb.collectAsState()
    val rightDb by recordManager.rightDb.collectAsState()
    val isClipping by recordManager.isClipping.collectAsState()
    val livePitch by recordManager.livePitch.collectAsState()
    val liveWaveform by recordManager.liveWaveform.collectAsState()
    val markers by recordManager.markers.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()

    val metronome = viewModel.metronome
    val isMetronomePlaying by metronome.isPlaying.collectAsState()
    val metronomeBpm by metronome.bpm.collectAsState()
    var showMetronomeDialog by remember { mutableStateOf(false) }

    val allNotes by viewModel.allNotes.collectAsState()
    val activeNote by viewModel.activeTeleprompterNote.collectAsState()
    var showTeleprompter by remember { mutableStateOf(false) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var saveTitle by remember { mutableStateOf("") }
    var saveTag by remember { mutableStateOf(selectedMode.title) }

    var micGain by remember { mutableFloatStateOf(1.0f) }
    var showEffectsSheet by remember { mutableStateOf(false) }
    var noiseReductionEnabled by remember { mutableStateOf(true) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            viewModel.startRecording()
        }
    }

    // Pulsing animation for the big recording button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (recordingState == RecordingState.RECORDING) 1.18f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Live Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Metronome toggle button
                    IconButton(
                        onClick = { showMetronomeDialog = true },
                        modifier = Modifier.testTag("record_metronome_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Metronome",
                            tint = if (isMetronomePlaying) StudioOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Teleprompter toggle button
                    IconButton(
                        onClick = { showTeleprompter = !showTeleprompter },
                        modifier = Modifier.testTag("record_teleprompter_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Teleprompter / Lyrics",
                            tint = if (showTeleprompter) StudioCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Effects / Gain quick sheet
                    IconButton(
                        onClick = { showEffectsSheet = !showEffectsSheet },
                        modifier = Modifier.testTag("record_settings_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Gain and Noise Reduction",
                            tint = if (showEffectsSheet) StudioOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // 1. Digital Big Timer & Mode Title (Matching Screenshot 1)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val formattedTime = formatTimerWithDeciseconds(recordingDurationMs)
                    Text(
                        text = formattedTime,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        color = if (recordingState == RecordingState.RECORDING) StudioOrange else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedMode.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Quick Effects Drawer if toggled
                AnimatedVisibility(visible = showEffectsSheet) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Microphone Gain", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("${"%.1f".format(micGain)}x", color = StudioOrange, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = micGain,
                                onValueChange = {
                                    micGain = it
                                    viewModel.setMicGain(it)
                                },
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(thumbColor = StudioOrange, activeTrackColor = StudioOrange)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Noise Suppression", style = MaterialTheme.typography.bodySmall)
                                Switch(
                                    checked = noiseReductionEnabled,
                                    onCheckedChange = {
                                        noiseReductionEnabled = it
                                        viewModel.setNoiseReduction(it)
                                    }
                                )
                            }
                        }
                    }
                }

                // 2. Timeline Ruler Markers (00:04, 00:05, 00:06, 00:07, 00:08)
                val currentSec = (recordingDurationMs / 1000).toInt()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (offset in -2..2) {
                        val sec = maxOf(0, currentSec + offset)
                        val markStr = "%02d:%02d".format(sec / 60, sec % 60)
                        Text(
                            text = markStr,
                            fontSize = 11.sp,
                            fontWeight = if (offset == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (offset == 0) StudioOrange else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                // 3. Scrolling Live Waveform Canvas with Central Red Cursor (Matching Screenshot 1)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    LiveWaveformCanvas(
                        waveformBars = liveWaveform,
                        durationMs = recordingDurationMs,
                        isRecording = recordingState == RecordingState.RECORDING,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 4. Live Vocal Pitch Tuner (for singers & vocals)
                AnimatedVisibility(visible = recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
                    PitchTunerDisplay(
                        pitchInfo = livePitch,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                // 5. Stereo Dual VU Meter with Overload Clip Detection
                StereoDualVuMeter(
                    leftDb = leftDb,
                    rightDb = rightDb,
                    isClipping = isClipping,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // Markers Tag Counter
                if (markers.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = StudioAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${markers.size} marker(s) added",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioAmber
                        )
                    }
                }

                // 5. Main Controls Row (Mark, Record/Pause, Save) - Exactly like Screenshot 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mark Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(
                            enabled = recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED
                        ) {
                            viewModel.addMarker()
                        }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = "Mark",
                                    tint = if (recordingState == RecordingState.RECORDING) StudioAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Mark",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Big Center Recording / Pause Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(90.dp)
                    ) {
                        // Pulsing outer halo when recording
                        if (recordingState == RecordingState.RECORDING) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .scale(pulseScale)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(StudioOrange.copy(alpha = 0.4f), Color.Transparent)
                                        ),
                                        CircleShape
                                    )
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                if (!hasPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    when (recordingState) {
                                        RecordingState.IDLE -> viewModel.startRecording()
                                        RecordingState.RECORDING -> viewModel.pauseRecording()
                                        RecordingState.PAUSED -> viewModel.resumeRecording()
                                        RecordingState.STOPPED -> viewModel.startRecording()
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .testTag("record_main_fab"),
                            containerColor = StudioOrange,
                            contentColor = Color.White,
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = when (recordingState) {
                                    RecordingState.RECORDING -> Icons.Default.Pause
                                    RecordingState.PAUSED -> Icons.Default.PlayArrow
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = "Record / Pause",
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    // Save Checkmark Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(
                            enabled = recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED
                        ) {
                            saveTitle = "${selectedMode.title}_Take"
                            showSaveDialog = true
                        }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save",
                                    tint = if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
                                        Color(0xFF00E676)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    },
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 6. Subtitle hint (Matching Screenshot 1)
                Text(
                    text = "You can continue recording for up to 4 h 48 min.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Floating Teleprompter & Lyrics Overlay
            TeleprompterOverlay(
                activeNote = activeNote,
                allNotes = allNotes,
                isOpen = showTeleprompter,
                onClose = { showTeleprompter = false },
                onSelectNote = { viewModel.selectTeleprompterNote(it) },
                onSaveNote = { title, content -> viewModel.saveTeleprompterNote(title, content) },
                onDeleteNote = { viewModel.deleteTeleprompterNote(it) },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Save Recording Dialog
            if (showSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text("Save Recording Take", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = saveTitle,
                                onValueChange = { saveTitle = it },
                                label = { Text("Recording Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Tag / Category", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("Music", "Voice", "Meeting", "Standard").forEach { tag ->
                                    FilterChip(
                                        selected = saveTag == tag,
                                        onClick = { saveTag = tag },
                                        label = { Text(tag) }
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.stopAndSaveRecording(saveTitle, saveTag)
                                showSaveDialog = false
                                onNavigateToLibrary()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioOrange)
                        ) {
                            Text("Save to Library")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSaveDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Metronome Control Dialog
            if (showMetronomeDialog) {
                AlertDialog(
                    onDismissRequest = { showMetronomeDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = StudioOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Studio Metronome", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$metronomeBpm BPM",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = StudioOrange
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Slider(
                                value = metronomeBpm.toFloat(),
                                onValueChange = { metronome.setBpm(it.toInt()) },
                                valueRange = 40f..240f,
                                colors = SliderDefaults.colors(thumbColor = StudioOrange, activeTrackColor = StudioOrange)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { metronome.toggle() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMetronomePlaying) Color(0xFFFF1744) else StudioOrange
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = if (isMetronomePlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isMetronomePlaying) "Stop Metronome" else "Start Click Track")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showMetronomeDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}

fun formatTimerWithDeciseconds(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format("%02d : %02d : %02d", hours, minutes, seconds)
}
