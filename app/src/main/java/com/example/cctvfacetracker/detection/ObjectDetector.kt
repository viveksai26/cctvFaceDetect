package com.example.cctvfacetracker.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetectorOptionsBase
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ObjectDetector(private val context: Context) {

    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptionsBase.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    suspend fun detect(bitmap: Bitmap): List<ObjectResult> = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val objects = Tasks.await(detector.process(image))
        objects.map { obj ->
            val labels = obj.labels.map { label ->
                ObjectLabel(label.text, label.confidence, label.index)
            }.sortedByDescending { it.confidence }
            
            ObjectResult(
                boundingBox = obj.boundingBox,
                trackingId = obj.trackingId,
                labels = labels
            )
        }
    }

    fun close() {
        detector.close()
    }

    data class ObjectResult(
        val boundingBox: Rect,
        val trackingId: Int?,
        val labels: List<ObjectLabel>
    ) {
        val bestLabel: ObjectLabel? = labels.firstOrNull()
        val centerX: Float = boundingBox.exactCenterX()
        val centerY: Float = boundingBox.exactCenterY()
        val width: Int = boundingBox.width()
        val height: Int = boundingBox.height()
        
        val category: DetectionCategory
            get() = bestLabel?.let { label ->
                when (label.text.lowercase()) {
                    "person", "human", "people" -> DetectionCategory.PERSON
                    "dog", "cat", "pet", "animal" -> DetectionCategory.PET
                    "package", "parcel", "box" -> DetectionCategory.PACKAGE
                    "car", "vehicle", "truck", "bike", "motorcycle" -> DetectionCategory.VEHICLE
                    else -> DetectionCategory.UNKNOWN
                }
            } ?: DetectionCategory.UNKNOWN
    }

    data class ObjectLabel(
        val text: String,
        val confidence: Float,
        val index: Int
    )

    enum class DetectionCategory {
        PERSON, PET, PACKAGE, VEHICLE, UNKNOWN
    }
}