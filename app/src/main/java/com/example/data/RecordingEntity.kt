package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val filePath: String,
    val cameraName: String,
    val durationSeconds: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val fileSize: Long,
    val resolution: String
)
