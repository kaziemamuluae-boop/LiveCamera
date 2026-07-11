package com.example.data

import kotlinx.coroutines.flow.Flow

class RecordingRepository(private val recordingDao: RecordingDao) {
    val allRecordings: Flow<List<RecordingEntity>> = recordingDao.getAllRecordings()

    suspend fun insertRecording(recording: RecordingEntity): Long {
        return recordingDao.insertRecording(recording)
    }

    suspend fun deleteRecording(id: Int) {
        recordingDao.deleteRecordingById(id)
    }

    suspend fun clearAll() {
        recordingDao.clearAllRecordings()
    }
}
