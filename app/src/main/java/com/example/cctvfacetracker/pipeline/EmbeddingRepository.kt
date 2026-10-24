package com.example.cctvfacetracker.pipeline

interface EmbeddingRepository {
    fun getEnrolledEmbeddings(): Map<String, FloatArray>
    suspend fun enroll(name: String, embedding: FloatArray)
}
