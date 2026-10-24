package com.example.cctvfacetracker.pipeline

import android.graphics.Bitmap

/** Fans a single pipeline callback out to several listeners (e.g. alerting + notifications). */
class CompositeListener(private val listeners: List<PipelineListener>) : PipelineListener {
    override fun onFaceDetected(face: PipelineFaceResult) {
        listeners.forEach { it.onFaceDetected(face) }
    }

    override fun onRecognitionResult(name: String, confidence: Float, image: Bitmap) {
        listeners.forEach { it.onRecognitionResult(name, confidence, image) }
    }
}
