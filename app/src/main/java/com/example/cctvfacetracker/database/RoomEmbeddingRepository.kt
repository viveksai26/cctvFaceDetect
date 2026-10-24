package com.example.cctvfacetracker.database

import android.graphics.Bitmap
import com.example.cctvfacetracker.pipeline.EmbeddingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomEmbeddingRepository(private val faceDao: FaceDao) : EmbeddingRepository {
    private var enrolled = mutableMapOf<String, MutableList<FloatArray>>()
    private val thumbnails = mutableMapOf<String, Bitmap>()

    override suspend fun loadAll() = withContext(Dispatchers.IO) {
        val loaded = mutableMapOf<String, MutableList<FloatArray>>()
        for (entity in faceDao.getAll()) {
            loaded.getOrPut(entity.name) { mutableListOf() }.add(entity.embedding)
        }
        enrolled = loaded
    }

    override fun getEnrolledEmbeddings(): Map<String, List<FloatArray>> = enrolled

    override fun getThumbnail(name: String): Bitmap? = thumbnails[name]

    override suspend fun enroll(name: String, embedding: FloatArray, thumbnail: Bitmap) = withContext(Dispatchers.IO) {
        enrolled.getOrPut(name) { mutableListOf() }.add(embedding)
        thumbnails[name] = thumbnail
        faceDao.insert(FaceEntity(name = name, embedding = embedding))
    }

    override suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        enrolled.remove(name)
        thumbnails.remove(name)
        faceDao.deleteByName(name)
    }

    override suspend fun rename(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        val samples = enrolled.remove(oldName) ?: return@withContext
        enrolled.getOrPut(newName) { mutableListOf() }.addAll(samples)
        thumbnails.remove(oldName)?.let { thumbnails[newName] = it }
        faceDao.renameByName(oldName, newName)
    }
}
