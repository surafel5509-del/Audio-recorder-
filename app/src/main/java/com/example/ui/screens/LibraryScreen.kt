package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.RecordingEntity
import com.example.ui.components.AudioPlayerBar
import com.example.ui.components.StaticWaveformCanvas
import com.example.ui.components.formatTime
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioOrange
import com.example.viewmodel.MainStudioViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainStudioViewModel,
    onNavigateToEditor: (RecordingEntity) -> Unit,
    onNavigateToMixer: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recordings by viewModel.filteredRecordings.collectAsState()
    val allRecordings by viewModel.allRecordings.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val playerManager = viewModel.playerManager
    val isPlaying by playerManager.isPlaying.collectAsState()
    val currentPosMs by playerManager.currentPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val currentTitle by playerManager.currentTitle.collectAsState()
    val playbackSpeed by playerManager.playbackSpeed.collectAsState()
    val isLooping by playerManager.isLooping.collectAsState()

    var showSearchField by remember { mutableStateOf(false) }
    var recordingToRename by remember { mutableStateOf<RecordingEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var recordingToDelete by remember { mutableStateOf<RecordingEntity?>(null) }
    var inspectingRecording by remember { mutableStateOf<RecordingEntity?>(null) }
    var showSortDialog by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf("DATE_DESC") }

    val sortedRecordings = remember(recordings, sortOrder) {
        when (sortOrder) {
            "TITLE_ASC" -> recordings.sortedBy { it.title.lowercase() }
            "DURATION_DESC" -> recordings.sortedByDescending { it.durationMs }
            "SIZE_DESC" -> recordings.sortedByDescending { it.fileSize }
            else -> recordings.sortedByDescending { it.createdAt }
        }
    }

    val tagsList = listOf("All", "Standard", "Music", "Voice", "Meeting", "Untagged")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearchField) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            placeholder = { Text("Search recordings...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Recorder",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = "Pro",
                                tint = StudioAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchField = !showSearchField }) {
                        Icon(
                            imageVector = if (showSearchField) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            // Persistent Audio Player Bottom Bar if playing or loaded
            if (currentTitle.isNotEmpty()) {
                AudioPlayerBar(
                    title = currentTitle,
                    isPlaying = isPlaying,
                    currentPosMs = currentPosMs,
                    durationMs = durationMs,
                    playbackSpeed = playbackSpeed,
                    isLooping = isLooping,
                    onTogglePlayPause = { playerManager.togglePlayPause() },
                    onSeek = { playerManager.seekTo(it) },
                    onSkip = { playerManager.skip(it) },
                    onCycleSpeed = {
                        val nextSpeed = when (playbackSpeed) {
                            1.0f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2.0f
                            2.0f -> 0.75f
                            else -> 1.0f
                        }
                        playerManager.setSpeed(nextSpeed)
                    },
                    onToggleLoop = { playerManager.toggleLoop() },
                    onClose = { playerManager.stop() }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter Tabs (Matching Screenshot 2: All, Untagged, Music, etc.)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tagsList) { tag ->
                    val isSelected = selectedTag == tag
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectedTag.value = tag },
                        label = { Text(tag, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StudioOrange,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Sub-header: Recording count and sort icons (Matching Screenshot 2: "8 recordings", search, sort)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${sortedRecordings.size} recordings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                Row {
                    IconButton(onClick = { showSortDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort recordings",
                            tint = if (sortOrder != "DATE_DESC") StudioOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (sortedRecordings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching recordings" else "No recordings found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Recordings you save in Studio will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedRecordings, key = { it.id }) { rec ->
                        RecordingCardItem(
                            recording = rec,
                            isCurrentlyPlaying = isPlaying && currentTitle == rec.title,
                            onPlay = { viewModel.playRecording(rec) },
                            onEdit = { onNavigateToEditor(rec) },
                            onLoadInMixer = {
                                viewModel.loadRecordingIntoMixerTrack(System.currentTimeMillis(), rec)
                                onNavigateToMixer()
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(rec) },
                            onInspect = { inspectingRecording = rec },
                            onRename = {
                                recordingToRename = rec
                                renameText = rec.title
                            },
                            onDelete = { recordingToDelete = rec },
                            onShare = {
                                val file = File(rec.filePath)
                                if (file.exists()) {
                                    try {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "audio/*"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Studio Audio"))
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        // Rename Dialog
        recordingToRename?.let { rec ->
            AlertDialog(
                onDismissRequest = { recordingToRename = null },
                title = { Text("Rename Recording", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (renameText.isNotBlank()) {
                                viewModel.renameRecording(rec, renameText)
                            }
                            recordingToRename = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioOrange)
                    ) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { recordingToRename = null }) { Text("Cancel") }
                }
            )
        }

        // Delete Confirmation Dialog
        recordingToDelete?.let { rec ->
            AlertDialog(
                onDismissRequest = { recordingToDelete = null },
                title = { Text("Delete Recording", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete '${rec.title}'? This cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteRecording(rec)
                            recordingToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744))
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { recordingToDelete = null }) { Text("Cancel") }
                }
            )
        }

        // Sort Order Dialog
        if (showSortDialog) {
            AlertDialog(
                onDismissRequest = { showSortDialog = false },
                title = { Text("Sort Recordings", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf(
                            "DATE_DESC" to "Date (Newest first)",
                            "TITLE_ASC" to "Name (A - Z)",
                            "DURATION_DESC" to "Duration (Longest first)",
                            "SIZE_DESC" to "File Size (Largest first)"
                        ).forEach { (key, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sortOrder = key
                                        showSortDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = sortOrder == key,
                                    onClick = {
                                        sortOrder = key
                                        showSortDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSortDialog = false }) { Text("Close") }
                }
            )
        }

        // Audio Inspector Dialog
        inspectingRecording?.let { rec ->
            AlertDialog(
                onDismissRequest = { inspectingRecording = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = StudioCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Audio Properties", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("File: ${rec.title}", fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        Text("Format: ${rec.format} (PCM 16-bit)")
                        Text("Sample Rate: ${rec.sampleRate} Hz")
                        Text("Channels: ${if (rec.channels == 2) "Stereo (2ch)" else "Mono (1ch)"}")
                        Text("Duration: ${formatTime(rec.durationMs)}")
                        Text("File Size: ${formatFileSize(rec.fileSize)} (${rec.fileSize} bytes)")
                        Text("Category: ${rec.tag}")
                        Text("Storage: ${rec.filePath}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { inspectingRecording = null },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioCyan)
                    ) {
                        Text("Done", color = Color.Black)
                    }
                }
            )
        }
    }
}

@Composable
fun RecordingCardItem(
    recording: RecordingEntity,
    isCurrentlyPlaying: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onLoadInMixer: () -> Unit,
    onToggleFavorite: () -> Unit,
    onInspect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val formattedDate = remember(recording.createdAt) { dateFormat.format(Date(recording.createdAt)) }
    val waveformPoints = remember(recording.waveformPoints) {
        if (recording.waveformPoints.isNotBlank()) {
            recording.waveformPoints.split(",").mapNotNull { it.trim().toIntOrNull() }
        } else emptyList()
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCurrentlyPlaying) StudioOrange else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .testTag("recording_item_${recording.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Waveform Coral/Peach Icon (Matching Screenshot 2)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(StudioCoral.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCurrentlyPlaying) Icons.Default.VolumeUp else Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (isCurrentlyPlaying) StudioOrange else StudioCoral,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Title and Details (00:06 114.87KB M4A 11-08 21:04)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = recording.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = if (isCurrentlyPlaying) StudioOrange else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (recording.isFavorite) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Favorite",
                                tint = StudioAmber,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatTime(recording.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("  ", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = formatFileSize(recording.fileSize),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("  ", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = recording.format,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = StudioCyan
                        )
                        Text("  ", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // Favorite Quick Star
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (recording.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (recording.isFavorite) StudioAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 3-Dots Menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play Take") },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onPlay()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit & Master") },
                            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open in Multi-Track") },
                            leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onLoadInMixer()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Audio Properties") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onInspect()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Audio") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShare()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFFF1744)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF1744)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            // Inline waveform preview if playing or active
            if (isCurrentlyPlaying && waveformPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                StaticWaveformCanvas(
                    waveformPoints = waveformPoints,
                    progress = 0.5f,
                    playedColor = StudioOrange,
                    unplayedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                )
            }
        }
    }
}
