package com.example.cctvfacetracker.pipeline

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * A single raw embedding comparison is noisy -- without face alignment, the same person's
 * similarity score can swing from a confident match to well below threshold between
 * consecutive sightings a couple of seconds apart (observed: 0.60 -> 0.37 for the same face).
 * This buffers the last [windowSize] raw results per track ID and only forwards a name once it
 * has won at least [minVotes] of them, so downstream (alerts, notifications, the dashboard)
 * sees a stable identity instead of per-frame flicker.
 */
class SmoothingListener(
    private val delegate: PipelineListener,
    private val windowSize: Int = 5,
    private val minVotes: Int = 3,
) : PipelineListener {
    private val history = ConcurrentHashMap<Int, ArrayDeque<String>>()

    override fun onFaceDetected(face: PipelineFaceResult) {
        delegate.onFaceDetected(face)
    }

    override fun onRecognitionResult(trackId: Int, name: String, confidence: Float, nearestCandidate: String?, image: Bitmap) {
        if (history.size > MAX_TRACKS_REMEMBERED) history.clear()

        val window = history.getOrPut(trackId) { ArrayDeque() }
        val smoothedName = synchronized(window) {
            window.addLast(name)
            while (window.size > windowSize) window.removeFirst()
            val best = window.groupingBy { it }.eachCount().maxBy { it.value }
            if (best.value >= minVotes) best.key else "Unknown"
        }
        // nearestCandidate is only meaningful alongside "Unknown" -- drop it once smoothing has
        // confirmed an actual match, so downstream never sees a name + a stale candidate hint.
        val outgoingCandidate = if (smoothedName == "Unknown") nearestCandidate else null
        delegate.onRecognitionResult(trackId, smoothedName, confidence, outgoingCandidate, image)
    }

    private companion object {
        const val MAX_TRACKS_REMEMBERED = 200
    }
}
