package com.example.cctvfacetracker.pipeline

/** Cosine similarity between two equal-length embeddings, in [-1, 1] (NaN if either is all-zero). */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    return dot / (Math.sqrt(normA.toDouble()).toFloat() * Math.sqrt(normB.toDouble()).toFloat())
}
