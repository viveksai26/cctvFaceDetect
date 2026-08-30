package com.example.cctvfacetracker.pipeline

import android.graphics.Bitmap

interface FaceDetector {
    fun detectFaces(frame: Bitmap): List<FaceResult>
}
