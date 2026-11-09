package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.data.model.RecordingNoteEntity
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun TeleprompterOverlay(
    activeNote: RecordingNoteEntity?,
    allNotes: List<RecordingNoteEntity>,
    isOpen: Boolean,
    onClose: () -> Unit,
    onSelectNote: (RecordingNoteEntity) -> Unit,
    onSaveNote: (title: String, content: String) -> Unit,
    onDeleteNote: (RecordingNoteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    var isEditing by remember { mutableStateOf(false) }
    var editTitle by remember(activeNote) { mutableStateOf(activeNote?.title ?: "New Script") }
    var editContent by remember(activeNote) { mutableStateOf(activeNote?.content ?: "") }

    var isAutoScrolling by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableFloatStateOf(activeNote?.scrollSpeed ?: 2.0f) }
    var fontSizeSp by remember { mutableIntStateOf(activeNote?.fontSizeSp ?: 20) }

    val scrollState = rememberScrollState()

    // Smooth Auto-scrolling coroutine
    LaunchedEffect(isAutoScrolling, scrollSpeed) {
        if (isAutoScrolling) {
            while (isActive && isAutoScrolling) {
                val scrollPixels = scrollSpeed * 2.5f
                scrollState.scrollBy(scrollPixels)
                delay(30)
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, StudioCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Teleprompter",
                        tint = StudioCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEditing) "Edit Script" else (activeNote?.title ?: "Teleprompter / Lyrics"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isEditing = !isEditing },
                        modifier = Modifier.testTag("teleprompter_edit_toggle")
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = if (isEditing) "Done" else "Edit",
                            tint = if (isEditing) StudioCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("teleprompter_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Script Selection Chips
            if (!isEditing && allNotes.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allNotes.take(3).forEach { note ->
                        val isSelected = activeNote?.id == note.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectNote(note) },
                            label = { Text(note.title, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StudioCyan.copy(alpha = 0.2f),
                                selectedLabelColor = StudioCyan
                            )
                        )
                    }
                }
            }

            // Content Area
            if (isEditing) {
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = { Text("Script Title") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editContent,
                    onValueChange = { editContent = it },
                    label = { Text("Lyrics, Poem or Recording Notes...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSaveNote(editTitle, editContent)
                        isEditing = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("teleprompter_save_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = StudioCyan)
                ) {
                    Text("Save Script & Lyrics", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else {
                // Read / Auto-scroll View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                        .verticalScroll(scrollState)
                ) {
                    val textToShow = activeNote?.content?.ifBlank { "No lyrics selected. Tap the edit icon to type or paste your lyrics and scripts!" }
                        ?: "Tap edit or select a script above to start reading while recording."
                    Text(
                        text = textToShow,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.5).sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Auto-scroll and Font controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Play / Pause auto-scroll
                    FilledTonalButton(
                        onClick = { isAutoScrolling = !isAutoScrolling },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isAutoScrolling) StudioOrange else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("teleprompter_autoscroll_toggle")
                    ) {
                        Icon(
                            imageVector = if (isAutoScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isAutoScrolling) "Pause" else "Auto-Scroll",
                            tint = if (isAutoScrolling) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAutoScrolling) "Pause Scroll" else "Auto-Scroll",
                            color = if (isAutoScrolling) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Font Size adjustments
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { fontSizeSp = (fontSizeSp - 2).coerceAtLeast(14) }
                        ) {
                            Text("A-", fontWeight = FontWeight.Bold)
                        }
                        Text("${fontSizeSp}sp", style = MaterialTheme.typography.bodySmall)
                        IconButton(
                            onClick = { fontSizeSp = (fontSizeSp + 2).coerceAtMost(32) }
                        ) {
                            Text("A+", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Speed Slider Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Speed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = scrollSpeed,
                        onValueChange = { scrollSpeed = it },
                        valueRange = 1f..5f,
                        steps = 4,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = StudioCyan, activeTrackColor = StudioCyan)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${"%.1f".format(scrollSpeed)}x", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
