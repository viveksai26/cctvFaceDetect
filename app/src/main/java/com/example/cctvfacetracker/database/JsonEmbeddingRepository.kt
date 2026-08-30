package com.example.cctvfacetracker.database

import android.content.Context
import com.example.cctvfacetracker.pipeline.EmbeddingRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JsonEmbeddingRepository(context: Context) : EmbeddingRepository {
    private val file = File(context.filesDir, "enrolled_faces.json")
    private var enrolled = mutableMapOf<String, FloatArray>()

    suspend fun loadAll() = withContext(Dispatchers.IO) {
        if (file.exists()) {
            val json = JSONObject(file.readText())
            val faces = json.getJSONArray("faces")
            for (i in 0 until faces.length()) {
                val face = faces.getJSONObject(i)
                val embedding = FloatArray(128)
                val embeddingJson = face.getJSONArray("embedding")
                for (j in 0 until 128) embedding[j] = embeddingJson.getDouble(j).toFloat()
                enrolled[face.getString("name")] = embedding
            }
        }
    }

    override fun getEnrolledEmbeddings(): Map<String, FloatArray> = enrolled

    override suspend fun enroll(name: String, embedding: FloatArray) = withContext(Dispatchers.IO) {
        enrolled[name] = embedding
        val json = JSONObject()
        val faces = JSONArray()
        for ((n, e) in enrolled) {
            val face = JSONObject()
            face.put("name", n)
            val emb = JSONArray()
            for (v in e) emb.put(v.toDouble())
            face.put("embedding", emb)
            faces.put(face)
        }
        json.put("faces", faces)
        file.writeText(json.toString())
    }
}
