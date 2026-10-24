package com.example.cctvfacetracker.pipeline

import android.graphics.Rect

/**
 * Greedy IoU-matching tracker: matches each detection to the existing tracker whose
 * last-known box has the highest overlap (above [iouThreshold]), highest-overlap pairs
 * winning first. Unmatched detections get a new id; unmatched trackers are dropped.
 */
class FaceTrackerImpl(private val iouThreshold: Float = 0.3f) : FaceTracker {
    private var nextId = 0
    private val activeTrackers = mutableMapOf<Int, Rect>()

    override fun updateTrackers(detectedFaces: List<Rect>): List<TrackedFace> {
        val candidates = mutableListOf<Triple<Int, Int, Float>>()
        for ((trackerId, trackedBox) in activeTrackers) {
            for ((detectionIndex, detection) in detectedFaces.withIndex()) {
                val score = iou(trackedBox, detection)
                if (score > iouThreshold) candidates.add(Triple(trackerId, detectionIndex, score))
            }
        }
        candidates.sortByDescending { it.third }

        val matchedTrackerIds = mutableSetOf<Int>()
        val matchedDetectionIndices = mutableSetOf<Int>()
        val updatedTrackers = mutableMapOf<Int, Rect>()
        val result = mutableListOf<TrackedFace>()

        for ((trackerId, detectionIndex, _) in candidates) {
            if (trackerId in matchedTrackerIds || detectionIndex in matchedDetectionIndices) continue
            matchedTrackerIds.add(trackerId)
            matchedDetectionIndices.add(detectionIndex)
            val box = detectedFaces[detectionIndex]
            updatedTrackers[trackerId] = box
            result.add(TrackedFace(trackerId, box))
        }

        for ((detectionIndex, box) in detectedFaces.withIndex()) {
            if (detectionIndex in matchedDetectionIndices) continue
            val id = nextId++
            updatedTrackers[id] = box
            result.add(TrackedFace(id, box))
        }

        activeTrackers.clear()
        activeTrackers.putAll(updatedTrackers)
        return result
    }

    private fun iou(a: Rect, b: Rect): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        if (right <= left || bottom <= top) return 0f
        val intersectArea = (right - left) * (bottom - top)
        val aArea = a.width() * a.height()
        val bArea = b.width() * b.height()
        val union = aArea + bArea - intersectArea
        return if (union <= 0) 0f else intersectArea.toFloat() / union
    }
}
