package com.example.cctvfacetracker.database

import android.content.Context
import com.example.cctvfacetracker.pipeline.EmbeddingRepository
import com.example.cctvfacetracker.security.KeystoreTextCipher
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Enrolled-face embeddings, encrypted at rest with an AndroidKeystore-backed key. */
class JsonEmbeddingRepository(context: Context) : EmbeddingRepository {
    private val file = File(context.filesDir, "enrolled_faces.enc")
    private val cipher = KeystoreTextCipher(KEY_ALIAS)
    private var enrolled = mutableMapOf<String, MutableList<FloatArray>>()

    override suspend fun loadAll() = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext
        val json = runCatching { JSONObject(cipher.decrypt(file.readText())) }.getOrNull() ?: return@withContext
        val faces = json.getJSONArray("faces")
        val loaded = mutableMapOf<String, MutableList<FloatArray>>()
        for (i in 0 until faces.length()) {
            val face = faces.getJSONObject(i)
            val embeddingJson = face.getJSONArray("embedding")
            val embedding = FloatArray(embeddingJson.length()) { j -> embeddingJson.getDouble(j).toFloat() }
            loaded.getOrPut(face.getString("name")) { mutableListOf() }.add(embedding)
        }
        enrolled = loaded
    }

    override fun getEnrolledEmbeddings(): Map<String, List<FloatArray>> = enrolled

    override suspend fun enroll(name: String, embedding: FloatArray) = withContext(Dispatchers.IO) {
        enrolled.getOrPut(name) { mutableListOf() }.add(embedding)
        persist()
    }

    override suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        enrolled.remove(name)
        persist()
    }

    override suspend fun rename(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        val samples = enrolled.remove(oldName)
        if (samples != null) {
            enrolled.getOrPut(newName) { mutableListOf() }.addAll(samples)
            persist()
        }
    }

    private fun persist() {
        val json = JSONObject()
        val faces = JSONArray()
        for ((name, samples) in enrolled) {
            for (embedding in samples) {
                val face = JSONObject()
                face.put("name", name)
                val emb = JSONArray()
                for (v in embedding) emb.put(v.toDouble())
                face.put("embedding", emb)
                faces.put(face)
            }
        }
        json.put("faces", faces)
        file.writeText(cipher.encrypt(json.toString()))
    }

    private companion object {
        const val KEY_ALIAS = "cctv_face_embeddings"
    }
}
