package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.audio.MixerTrackData
import com.example.audio.StudioBackingTracks
import com.example.data.model.RecordingEntity
import com.example.ui.components.StaticWaveformCanvas
import com.example.ui.components.formatTime
import com.example.ui.theme.*
import com.example.viewmodel.MainStudioViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixerStudioScreen(
    viewModel: MainStudioViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mixerEngine = viewModel.mixerEngine
    val tracks by viewModel.mixerTracksList.collectAsState()
    val isPlaying by mixerEngine.isPlaying.collectAsState()
    val playPositionMs by mixerEngine.playbackPositionMs.collectAsState()
    val totalDurationMs by mixerEngine.totalDurationMs.collectAsState()
    val allRecordings by viewModel.allRecordings.collectAsState()

    var showAddTrackDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportTitle by remember { mutableStateOf("Studio_Master_Mix") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Multi-Track Mixer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${tracks.size} active tracks • 120 BPM",
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
                    val metronome = viewModel.metronome
                    val isMetronomeOn by metronome.isPlaying.collectAsState()
                    IconButton(onClick = { metronome.toggle() }) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Metronome",
                            tint = if (isMetronomeOn) StudioOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = { showExportDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = StudioCyan.copy(alpha = 0.2f)),
                        modifier = Modifier.testTag("mixer_export_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export", color = StudioCyan, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            // Master Transport Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Timeline Scrubber
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(playPositionMs),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = StudioOrange
                        )
                        val progress = if (totalDurationMs > 0) (playPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
                        Slider(
                            value = progress,
                            onValueChange = { ratio ->
                                val target = (ratio * totalDurationMs).toLong()
                                mixerEngine.seekTo(target)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = StudioOrange, activeTrackColor = StudioOrange)
                        )
                        Text(
                            text = formatTime(totalDurationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Transport controls & Add track
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showAddTrackDialog = true },
                            modifier = Modifier.testTag("mixer_add_track_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Track")
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { mixerEngine.stopPlayback() }) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FloatingActionButton(
                                onClick = {
                                    if (isPlaying) {
                                        mixerEngine.stopPlayback()
                                    } else {
                                        mixerEngine.startPlayback(playPositionMs)
                                    }
                                },
                                shape = CircleShape,
                                containerColor = StudioOrange,
                                contentColor = Color.White,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("mixer_play_pause_fab")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play"
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(6.dp)) }

            if (tracks.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(52.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No tracks loaded", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Add vocal takes, guitar backing chords, or drum beats to start mixing in multi-track!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showAddTrackDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan)
                            ) {
                                Text("Choose Audio or Backing Beat", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(tracks) { track ->
                    MixerTrackCard(
                        track = track,
                        onVolumeChange = { viewModel.updateMixerTrackVolume(track.id, it) },
                        onPanChange = { viewModel.updateMixerTrackPan(track.id, it) },
                        onToggleMute = { viewModel.toggleMixerTrackMute(track.id) },
                        onToggleSolo = { viewModel.toggleMixerTrackSolo(track.id) },
                        onOffsetChange = { viewModel.updateMixerTrackOffset(track.id, it) },
                        onDelete = { viewModel.removeMixerTrack(track.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }

        // Add Track Dialog (Recordings or Preset Backing Tracks)
        if (showAddTrackDialog) {
            AlertDialog(
                onDismissRequest = { showAddTrackDialog = false },
                title = { Text("Add Audio Track", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("PRESET BACKING TRACKS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = StudioOrange)
                        Spacer(modifier = Modifier.height(8.dp))
                        StudioBackingTracks.PRESET_BACKING_TRACKS.forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.loadPresetBackingTrackIntoMixer(System.currentTimeMillis(), preset)
                                        showAddTrackDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(preset.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text(preset.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        if (allRecordings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("YOUR RECORDINGS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = StudioCyan)
                            Spacer(modifier = Modifier.height(8.dp))
                            allRecordings.take(4).forEach { rec ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            viewModel.loadRecordingIntoMixerTrack(System.currentTimeMillis(), rec)
                                            showAddTrackDialog = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = null, tint = StudioOrange, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(rec.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddTrackDialog = false }) { Text("Close") }
                }
            )
        }

        // Export Master Mix Dialog
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text("Export Master Multi-Track Mix", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Mixes all active tracks with volume, stereo panning, and soft limiting.", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = exportTitle,
                            onValueChange = { exportTitle = it },
                            label = { Text("Master Track Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.exportMixdown(exportTitle)
                            showExportDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioCyan)
                    ) {
                        Text("Export Stereo WAV", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExportDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun MixerTrackCard(
    track: MixerTrackData,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onOffsetChange: (Long) -> Unit,
    onDelete: () -> Unit
) {
    val trackColor = try {
        Color(android.graphics.Color.parseColor(track.colorHex))
    } catch (e: Exception) {
        StudioOrange
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, trackColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Track Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(trackColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Mute Button
                    FilterChip(
                        selected = track.isMuted,
                        onClick = onToggleMute,
                        label = { Text("M", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFF1744),
                            selectedLabelColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Solo Button
                    FilterChip(
                        selected = track.isSolo,
                        onClick = onToggleSolo,
                        label = { Text("S", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StudioAmber,
                            selectedLabelColor = Color.Black
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Track", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Track Audio Waveform Strip
            if (track.samples.isNotEmpty()) {
                val points = remember(track.samples.size) {
                    val step = (track.samples.size / 50).coerceAtLeast(1)
                    List(50) { i ->
                        val idx = i * step
                        val amp = if (idx < track.samples.size) kotlin.math.abs(track.samples[idx].toInt()) else 0
                        ((amp / 32768f) * 90f + 10f).toInt().coerceIn(10, 100)
                    }
                }
                StaticWaveformCanvas(
                    waveformPoints = points,
                    progress = 0f,
                    playedColor = trackColor,
                    unplayedColor = trackColor.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Volume Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vol ${(track.volume * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(65.dp)
                )
                Slider(
                    value = track.volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0.0f..1.5f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = trackColor, activeTrackColor = trackColor)
                )
            }

            // Stereo Pan Slider (L to R)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val panLabel = when {
                    track.pan < -0.1f -> "L ${(kotlin.math.abs(track.pan) * 100).toInt()}%"
                    track.pan > 0.1f -> "R ${(track.pan * 100).toInt()}%"
                    else -> "Center"
                }
                Text(
                    text = "Pan $panLabel",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(65.dp)
                )
                Slider(
                    value = track.pan,
                    onValueChange = onPanChange,
                    valueRange = -1.0f..1.0f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = StudioCyan, activeTrackColor = StudioCyan)
                )
            }

            // Timeline Offset adjustment
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Sync Offset: +${track.offsetMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    TextButton(onClick = { onOffsetChange((track.offsetMs - 250).coerceAtLeast(0L)) }) {
                        Text("-250ms", fontSize = 11.sp)
                    }
                    TextButton(onClick = { onOffsetChange(track.offsetMs + 250) }) {
                        Text("+250ms", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
