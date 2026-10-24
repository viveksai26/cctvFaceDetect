package com.example.cctvfacetracker.database

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.cctvfacetracker.pipeline.EmbeddingRepository
import com.example.cctvfacetracker.security.KeystoreTextCipher
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Enrolled-face embeddings (+ a representative thumbnail per name), encrypted at rest with an AndroidKeystore-backed key. */
class JsonEmbeddingRepository(context: Context) : EmbeddingRepository {
    private val file = File(context.filesDir, "enrolled_faces.enc")
    private val cipher = KeystoreTextCipher(KEY_ALIAS)
    private var enrolled = mutableMapOf<String, MutableList<FloatArray>>()
    private var thumbnails = mutableMapOf<String, Bitmap>()

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

        val loadedThumbnails = mutableMapOf<String, Bitmap>()
        val thumbnailsJson = json.optJSONObject("thumbnails")
        if (thumbnailsJson != null) {
            for (name in thumbnailsJson.keys()) {
                base64ToBitmap(thumbnailsJson.getString(name))?.let { loadedThumbnails[name] = it }
            }
        }
        thumbnails = loadedThumbnails
    }

    override fun getEnrolledEmbeddings(): Map<String, List<FloatArray>> = enrolled

    override fun getThumbnail(name: String): Bitmap? = thumbnails[name]

    override suspend fun enroll(name: String, embedding: FloatArray, thumbnail: Bitmap) = withContext(Dispatchers.IO) {
        enrolled.getOrPut(name) { mutableListOf() }.add(embedding)
        thumbnails[name] = downscale(thumbnail)
        persist()
    }

    override suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        enrolled.remove(name)
        thumbnails.remove(name)
        persist()
    }

    override suspend fun rename(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        val samples = enrolled.remove(oldName)
        if (samples != null) {
            enrolled.getOrPut(newName) { mutableListOf() }.addAll(samples)
            thumbnails.remove(oldName)?.let { thumbnails[newName] = it }
            persist()
        }
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= THUMBNAIL_MAX_DIMENSION && bitmap.height <= THUMBNAIL_MAX_DIMENSION) return bitmap
        val scale = THUMBNAIL_MAX_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun base64ToBitmap(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

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

        val thumbnailsJson = JSONObject()
        for ((name, bitmap) in thumbnails) {
            thumbnailsJson.put(name, bitmapToBase64(bitmap))
        }
        json.put("thumbnails", thumbnailsJson)

        file.writeText(cipher.encrypt(json.toString()))
    }

    private companion object {
        const val KEY_ALIAS = "cctv_face_embeddings"
        const val THUMBNAIL_MAX_DIMENSION = 160
    }
}
