package com.example.cctvfacetracker.database

import com.example.cctvfacetracker.pipeline.EmbeddingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomEmbeddingRepository(private val faceDao: FaceDao) : EmbeddingRepository {
    private var enrolled = mutableMapOf<String, MutableList<FloatArray>>()

    override suspend fun loadAll() = withContext(Dispatchers.IO) {
        val loaded = mutableMapOf<String, MutableList<FloatArray>>()
        for (entity in faceDao.getAll()) {
            loaded.getOrPut(entity.name) { mutableListOf() }.add(entity.embedding)
        }
        enrolled = loaded
    }

    override fun getEnrolledEmbeddings(): Map<String, List<FloatArray>> = enrolled

    override suspend fun enroll(name: String, embedding: FloatArray) = withContext(Dispatchers.IO) {
        enrolled.getOrPut(name) { mutableListOf() }.add(embedding)
        faceDao.insert(FaceEntity(name = name, embedding = embedding))
    }

    override suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        enrolled.remove(name)
        faceDao.deleteByName(name)
    }

    override suspend fun rename(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        val samples = enrolled.remove(oldName) ?: return@withContext
        enrolled.getOrPut(newName) { mutableListOf() }.addAll(samples)
        faceDao.renameByName(oldName, newName)
    }
}
