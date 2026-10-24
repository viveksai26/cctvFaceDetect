package com.example.cctvfacetracker.pipeline

interface EmbeddingRepository {
    /** (Re)loads enrolled embeddings from storage into memory. */
    suspend fun loadAll()

    /** Each enrolled name maps to one or more sample embeddings; recognition matches against all. */
    fun getEnrolledEmbeddings(): Map<String, List<FloatArray>>
    suspend fun enroll(name: String, embedding: FloatArray)
    suspend fun delete(name: String)
    suspend fun rename(oldName: String, newName: String)
}
