package com.example.cctvfacetracker.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomEmbeddingRepository(private val faceDao: FaceDao) {
    private var enrolled = mutableMapOf<String, FloatArray>()

    suspend fun loadAll() = withContext(Dispatchers.IO) {
        enrolled = faceDao.getAll().associate { it.name to it.embedding }.toMutableMap()
    }

    fun getEnrolledEmbeddings(): Map<String, FloatArray> = enrolled

    suspend fun enroll(name: String, embedding: FloatArray) = withContext(Dispatchers.IO) {
        enrolled[name] = embedding
        faceDao.insert(FaceEntity(name = name, embedding = embedding))
    }
}
