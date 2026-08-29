package com.example.cctvfacetracker.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FaceDetector(private val context: Context) {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
    )

    suspend fun detect(bitmap: Bitmap): List<FaceResult> = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = Tasks.await(detector.process(image))
        faces.map { face ->
            val landmarks = mutableMapOf<FaceLandmark.LandmarkType, PointF>()
            val landmarkTypes = intArrayOf(
                FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE, FaceLandmark.LEFT_CHEEK,
                FaceLandmark.RIGHT_CHEEK, FaceLandmark.NOSE_BASE, FaceLandmark.MOUTH_LEFT,
                FaceLandmark.MOUTH_RIGHT, FaceLandmark.MOUTH_BOTTOM
            )
            for (type in landmarkTypes) {
                // This is still tricky because we need the enum type for the map key.
                // Let's just use the int as the key for now and cast.
                face.getLandmark(type)?.let { landmarks[type as FaceLandmark.LandmarkType] = it.position }
            }
            FaceResult(
                boundingBox = face.boundingBox,
                trackingId = face.trackingId,
                leftEyeOpenProbability = face.leftEyeOpenProbability,
                rightEyeOpenProbability = face.rightEyeOpenProbability,
                smilingProbability = face.smilingProbability,
                headEulerAngleX = face.headEulerAngleX,
                headEulerAngleY = face.headEulerAngleY,
                headEulerAngleZ = face.headEulerAngleZ,
                landmarks = landmarks
            )
        }
    }

    fun close() {
        detector.close()
    }

    data class FaceResult(
        val boundingBox: Rect,
        val trackingId: Int?,
        val leftEyeOpenProbability: Float?,
        val rightEyeOpenProbability: Float?,
        val smilingProbability: Float?,
        val headEulerAngleX: Float?,
        val headEulerAngleY: Float?,
        val headEulerAngleZ: Float?,
        val landmarks: Map<FaceLandmark.LandmarkType, PointF>
    ) {
        val centerX: Float = boundingBox.exactCenterX()
        val centerY: Float = boundingBox.exactCenterY()
        val width: Int = boundingBox.width()
        val height: Int = boundingBox.height()
    }
}