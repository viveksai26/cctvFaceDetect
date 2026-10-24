package com.example.cctvfacetracker.pipeline

import android.graphics.Bitmap
import android.graphics.Rect

class FaceProcessor(
    private val detector: FaceDetector,
    private val tracker: FaceTracker,
    private val recognizer: FaceRecognizer,
    private val embeddingRepo: EmbeddingRepository
) : FacePipeline {
    private var listener: PipelineListener? = null

    override fun processFrame(frame: Bitmap) {
        try {
            android.util.Log.d("TRACKING_PIPELINE", "TRACKING_PIPELINE processFrame START bitmap=${frame.width}x${frame.height}")
            val detectedFaces = detector.detectFaces(frame)
            android.util.Log.d("TRACKING_PIPELINE", "TRACKING_PIPELINE processFrame RESULT detections=${detectedFaces.size}")
            
            val rects = detectedFaces.map { Rect(it.boundingBox.left.toInt(), it.boundingBox.top.toInt(), it.boundingBox.right.toInt(), it.boundingBox.bottom.toInt()) }
            val trackedFaces = tracker.updateTrackers(rects)
            
            for ((index, face) in trackedFaces.withIndex()) {
                android.util.Log.d("TRACKING_PIPELINE", "TRACKING_FACE_DETECTED index=$index")
                listener?.onFaceDetected(PipelineFaceResult(face.id, face.boundingBox))
                
                android.util.Log.d("TRACKING_SFACE", "TRACKING_SFACE START")
                val faceBitmap = Bitmap.createBitmap(frame, face.boundingBox.left.coerceIn(0, frame.width - 1), face.boundingBox.top.coerceIn(0, frame.height - 1), 
                    face.boundingBox.width().coerceIn(1, frame.width - face.boundingBox.left.toInt()), 
                    face.boundingBox.height().coerceIn(1, frame.height - face.boundingBox.top.toInt()))
                android.util.Log.d("TRACKING_SFACE", "TRACKING_SFACE RESULT")
                
                val enrolled = embeddingRepo.getEnrolledEmbeddings()
                android.util.Log.d("TRACKING_RECOGNITION", "TRACKING_RECOGNITION START enrolled=${enrolled.size}")
                val result = recognizer.recognize(faceBitmap, enrolled)
                android.util.Log.d("TRACKING_RECOGNITION", "TRACKING_RECOGNITION RESULT name=${result?.first ?: "null"}")
                
                if (result != null) {
                    listener?.onRecognitionResult(result.first, result.second, faceBitmap)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TRACKING_PIPELINE", "TRACKING_PIPELINE ERROR: ${e.message}", e)
        }
    }

    override fun setListener(listener: PipelineListener) {
        this.listener = listener
    }
}
