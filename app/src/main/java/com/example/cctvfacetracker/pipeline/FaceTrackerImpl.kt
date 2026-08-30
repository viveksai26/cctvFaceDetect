package com.example.cctvfacetracker.pipeline

import android.graphics.Rect

/**
 * Simple IoU-based tracker implementation template.
 */
class FaceTrackerImpl : FaceTracker {
    private var nextId = 0
    private val activeTrackers = mutableMapOf<Int, Rect>()

    override fun updateTrackers(detectedFaces: List<Rect>): List<TrackedFace> {
        // TEMPLATE: Implement IoU matching logic here:
        // 1. Calculate IoU between detectedFaces and existing activeTrackers
        // 2. If IoU > threshold, update existing tracker ID
        // 3. If no match, assign new ID
        
        return detectedFaces.map { TrackedFace(nextId++, it) }
    }
}
