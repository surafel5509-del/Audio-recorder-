package com.example.data.local

import androidx.room.*
import com.example.data.model.ProjectTrackEntity
import com.example.data.model.RecordingEntity
import com.example.data.model.RecordingNoteEntity
import com.example.data.model.StudioProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE tag = :tag ORDER BY createdAt DESC")
    fun getRecordingsByTag(tag: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getRecordingById(id: Long): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Update
    suspend fun updateRecording(recording: RecordingEntity)

    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface StudioProjectDao {
    @Query("SELECT * FROM studio_projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<StudioProjectEntity>>

    @Query("SELECT * FROM studio_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Long): StudioProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: StudioProjectEntity): Long

    @Update
    suspend fun updateProject(project: StudioProjectEntity)

    @Delete
    suspend fun deleteProject(project: StudioProjectEntity)

    // Tracks
    @Query("SELECT * FROM project_tracks WHERE projectId = :projectId ORDER BY trackIndex ASC")
    fun getTracksForProject(projectId: Long): Flow<List<ProjectTrackEntity>>

    @Query("SELECT * FROM project_tracks WHERE projectId = :projectId ORDER BY trackIndex ASC")
    suspend fun getTracksForProjectList(projectId: Long): List<ProjectTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: ProjectTrackEntity): Long

    @Update
    suspend fun updateTrack(track: ProjectTrackEntity)

    @Delete
    suspend fun deleteTrack(track: ProjectTrackEntity)

    @Query("DELETE FROM project_tracks WHERE projectId = :projectId")
    suspend fun deleteTracksForProject(projectId: Long)
}

@Dao
interface RecordingNoteDao {
    @Query("SELECT * FROM recording_notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<RecordingNoteEntity>>

    @Query("SELECT * FROM recording_notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): RecordingNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: RecordingNoteEntity): Long

    @Update
    suspend fun updateNote(note: RecordingNoteEntity)

    @Delete
    suspend fun deleteNote(note: RecordingNoteEntity)
}
