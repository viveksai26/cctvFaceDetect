package com.example.cctvfacetracker.pipeline

import android.graphics.Bitmap

interface EmbeddingRepository {
    /** (Re)loads enrolled embeddings from storage into memory. */
    suspend fun loadAll()

    /** Each enrolled name maps to one or more sample embeddings; recognition matches against all. */
    fun getEnrolledEmbeddings(): Map<String, List<FloatArray>>

    /** A representative face photo for this name (the most recently enrolled sample), if any. */
    fun getThumbnail(name: String): Bitmap?

    suspend fun enroll(name: String, embedding: FloatArray, thumbnail: Bitmap)
    suspend fun delete(name: String)
    suspend fun rename(oldName: String, newName: String)
}
