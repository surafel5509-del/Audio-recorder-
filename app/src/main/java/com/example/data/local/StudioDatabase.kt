package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.ProjectTrackEntity
import com.example.data.model.RecordingEntity
import com.example.data.model.RecordingNoteEntity
import com.example.data.model.StudioProjectEntity

@Database(
    entities = [
        RecordingEntity::class,
        StudioProjectEntity::class,
        ProjectTrackEntity::class,
        RecordingNoteEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun studioProjectDao(): StudioProjectDao
    abstract fun recordingNoteDao(): RecordingNoteDao

    companion object {
        @Volatile
        private var INSTANCE: StudioDatabase? = null

        fun getInstance(context: Context): StudioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StudioDatabase::class.java,
                    "provoice_studio.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
