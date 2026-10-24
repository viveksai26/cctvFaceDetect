package com.example.cctvfacetracker.pipeline

import android.graphics.Bitmap

interface FacePipeline {
    fun processFrame(frame: Bitmap)
    fun setListener(listener: PipelineListener)
}

interface PipelineListener {
    fun onFaceDetected(face: PipelineFaceResult)
    fun onRecognitionResult(name: String, confidence: Float, image: android.graphics.Bitmap)
}

data class PipelineFaceResult(val id: Int, val boundingBox: android.graphics.Rect)
