package com.example.cctvfacetracker.pipeline

import android.graphics.Bitmap

interface FaceRecognizer {
    fun extractEmbedding(faceImage: Bitmap): FloatArray
    fun recognize(faceImage: Bitmap, enrolledEmbeddings: Map<String, List<FloatArray>>): RecognitionOutcome
}

/**
 * [name] is "Unknown" unless [confidence] cleared the match threshold -- unchanged semantics
 * from before. [nearestCandidate] is set only for a near-miss (a name whose best sample scored
 * above the near-miss threshold but below the match threshold), so the enrollment UI can offer
 * "is this already [nearestCandidate]?" instead of silently treating it as a brand new person.
 */
data class RecognitionOutcome(
    val name: String,
    val confidence: Float,
    val nearestCandidate: String? = null,
)
