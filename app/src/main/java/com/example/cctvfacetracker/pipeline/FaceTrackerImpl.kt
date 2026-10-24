package com.example.cctvfacetracker.pipeline

import android.graphics.Rect

/**
 * Plain-Kotlin mirror of [android.graphics.Rect] so the matching algorithm is unit-testable
 * without Robolectric -- the JVM unit-test stub jar throws for real Rect method calls.
 */
internal data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top

    // Built via the no-arg constructor + direct field assignment rather than Rect(l,t,r,b):
    // that 4-arg constructor is stubbed to a no-op under the plain JVM unit-test runner (fields
    // stay 0 regardless of the arguments), while field writes are not. Identical in production.
    fun toRect(): Rect = Rect().apply { left = this@IntRect.left; top = this@IntRect.top; right = this@IntRect.right; bottom = this@IntRect.bottom }
}

internal fun intersectionOverUnion(a: IntRect, b: IntRect): Float {
    val left = maxOf(a.left, b.left)
    val top = maxOf(a.top, b.top)
    val right = minOf(a.right, b.right)
    val bottom = minOf(a.bottom, b.bottom)
    if (right <= left || bottom <= top) return 0f
    val intersectArea = (right - left) * (bottom - top)
    val aArea = a.width * a.height
    val bArea = b.width * b.height
    val union = aArea + bArea - intersectArea
    return if (union <= 0) 0f else intersectArea.toFloat() / union
}

/**
 * Greedy IoU-matching tracker: matches each detection to the existing tracker whose
 * last-known box has the highest overlap (above [iouThreshold]), highest-overlap pairs
 * winning first. Unmatched detections get a new id; unmatched trackers are dropped.
 */
class FaceTrackerImpl(private val iouThreshold: Float = 0.3f) : FaceTracker {
    private var nextId = 0
    private val activeTrackers = mutableMapOf<Int, IntRect>()

    override fun updateTrackers(detectedFaces: List<Rect>): List<TrackedFace> {
        val detections = detectedFaces.map { IntRect(it.left, it.top, it.right, it.bottom) }

        val candidates = mutableListOf<Triple<Int, Int, Float>>()
        for ((trackerId, trackedBox) in activeTrackers) {
            for ((detectionIndex, detection) in detections.withIndex()) {
                val score = intersectionOverUnion(trackedBox, detection)
                if (score > iouThreshold) candidates.add(Triple(trackerId, detectionIndex, score))
            }
        }
        candidates.sortByDescending { it.third }

        val matchedTrackerIds = mutableSetOf<Int>()
        val matchedDetectionIndices = mutableSetOf<Int>()
        val updatedTrackers = mutableMapOf<Int, IntRect>()
        val result = mutableListOf<TrackedFace>()

        for ((trackerId, detectionIndex, _) in candidates) {
            if (trackerId in matchedTrackerIds || detectionIndex in matchedDetectionIndices) continue
            matchedTrackerIds.add(trackerId)
            matchedDetectionIndices.add(detectionIndex)
            val box = detections[detectionIndex]
            updatedTrackers[trackerId] = box
            result.add(TrackedFace(trackerId, box.toRect()))
        }

        for ((detectionIndex, box) in detections.withIndex()) {
            if (detectionIndex in matchedDetectionIndices) continue
            val id = nextId++
            updatedTrackers[id] = box
            result.add(TrackedFace(id, box.toRect()))
        }

        activeTrackers.clear()
        activeTrackers.putAll(updatedTrackers)
        return result
    }
}
