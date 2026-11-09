package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val format: String = "WAV",
    val durationMs: Long = 0L,
    val fileSize: Long = 0L,
    val sampleRate: Int = 44100,
    val channels: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val tag: String = "Untagged",
    val folder: String = "Default",
    val waveformPoints: String = "", // Comma-separated normalized integers 0..100
    val notes: String = "",
    val isFavorite: Boolean = false
)

@Entity(tableName = "studio_projects")
data class StudioProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val bpm: Int = 120,
    val folder: String = "Projects"
)

@Entity(tableName = "project_tracks")
data class ProjectTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val trackIndex: Int,
    val name: String,
    val filePath: String = "",
    val volume: Float = 1.0f,
    val pan: Float = 0.0f, // -1.0 (Left) to +1.0 (Right)
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val offsetMs: Long = 0L,
    val durationMs: Long = 0L,
    val colorHex: String = "#FF5722"
)

@Entity(tableName = "recording_notes")
data class RecordingNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val scrollSpeed: Float = 2.0f,
    val fontSizeSp: Int = 18,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
