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
        val detectedFaces = detector.detectFaces(frame)
        val rects = detectedFaces.map { Rect(it.boundingBox.left.toInt(), it.boundingBox.top.toInt(), it.boundingBox.right.toInt(), it.boundingBox.bottom.toInt()) }
        val trackedFaces = tracker.updateTrackers(rects)
        
        for (face in trackedFaces) {
            listener?.onFaceDetected(PipelineFaceResult(face.id, face.boundingBox))
            
            // Extract face image for recognition
            val faceBitmap = Bitmap.createBitmap(frame, face.boundingBox.left.coerceIn(0, frame.width - 1), face.boundingBox.top.coerceIn(0, frame.height - 1), 
                face.boundingBox.width().coerceIn(1, frame.width - face.boundingBox.left.toInt()), 
                face.boundingBox.height().coerceIn(1, frame.height - face.boundingBox.top.toInt()))
            
            val result = recognizer.recognize(faceBitmap, embeddingRepo.getEnrolledEmbeddings())
            if (result != null) {
                listener?.onRecognitionResult(result.first, result.second, faceBitmap)
            }
        }
    }

    override fun setListener(listener: PipelineListener) {
        this.listener = listener
    }
}
