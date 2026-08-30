package com.example.cctvfacetracker.pipeline

import android.graphics.Bitmap

interface FaceRecognizer {
    fun extractEmbedding(faceImage: Bitmap): FloatArray
    fun recognize(faceImage: Bitmap, enrolledEmbeddings: Map<String, FloatArray>): Pair<String, Float>?
}
