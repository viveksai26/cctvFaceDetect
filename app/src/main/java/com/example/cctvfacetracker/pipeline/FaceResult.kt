package com.example.cctvfacetracker.pipeline

import android.graphics.PointF
import android.graphics.RectF

data class FaceResult(
    val boundingBox: RectF,
    val confidence: Float,
    val leftEye: PointF,
    val rightEye: PointF,
    val nose: PointF,
    val leftMouth: PointF,
    val rightMouth: PointF,
    var name: String = "Unknown",
    var embedding: FloatArray? = null
)
