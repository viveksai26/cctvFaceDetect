package com.example.cctvfacetracker.pipeline

import android.graphics.Rect

interface FaceTracker {
    fun updateTrackers(detectedFaces: List<Rect>): List<TrackedFace>
}

data class TrackedFace(val id: Int, val boundingBox: Rect)
