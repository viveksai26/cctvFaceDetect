package com.example.cctvfacetracker.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.tasks.await
import com.google.android.gms.tasks.Task

class DetectionManager(private val context: Context) {

    private val faceDetector = FaceDetector(context)
    private val objectDetector = ObjectDetector(context)
    private var frameExtractor: FrameExtractor? = null
    
    private val _detections = MutableStateFlow<List<TrackedDetection>>(emptyList())
    val detections = _detections.asStateFlow()
    
    private val _stats = MutableStateFlow(DetectionStats())
    val stats = _stats.asStateFlow()
    
    private var isRunning = false
    private var frameChannel: ReceiveChannel<Bitmap>? = null

    data class TrackedDetection(
        val id: String,
        val type: DetectionType,
        val boundingBox: android.graphics.Rect,
        val confidence: Float,
        val timestamp: Long = System.currentTimeMillis(),
        val faceDetails: FaceDetails? = null,
        val objectDetails: ObjectDetails? = null
    ) {
        val centerX: Float = boundingBox.exactCenterX()
        val centerY: Float = boundingBox.exactCenterY()
    }

    enum class DetectionType {
        FACE, PERSON, PET, PACKAGE, VEHICLE, UNKNOWN
    }

    data class FaceDetails(
        val trackingId: Int?,
        val leftEyeOpenProbability: Float?,
        val rightEyeOpenProbability: Float?,
        val smilingProbability: Float?,
        val headEulerAngleX: Float?,
        val headEulerAngleY: Float?,
        val headEulerAngleZ: Float?,
        val landmarks: Map<FaceLandmark.LandmarkType, android.graphics.PointF>
    )

    data class ObjectDetails(
        val trackingId: Int?,
        val labels: List<ObjectDetector.ObjectLabel>,
        val category: ObjectDetector.DetectionCategory
    )

    data class DetectionStats(
        val personCount: Int = 0,
        val petCount: Int = 0,
        val packageCount: Int = 0,
        val vehicleCount: Int = 0,
        val faceCount: Int = 0,
        val totalDetections: Int = 0,
        val lastUpdate: Long = 0
    )

    fun startTracking(player: ExoPlayer) {
        if (isRunning) return
        
        isRunning = true
        
        frameExtractor = FrameExtractor(context)
        frameChannel = frameExtractor!!.start(player)
        
        CoroutineScope(Dispatchers.IO).launch {
            frameChannel?.consumeEach { bitmap ->
                if (!isRunning) return@consumeEach
                processFrame(bitmap)
            }
        }
    }

    fun stopTracking() {
        isRunning = false
        frameExtractor?.stop()
        frameExtractor = null
        frameChannel = null
        _detections.value = emptyList()
    }

    private suspend fun processFrame(bitmap: Bitmap) {
        val timestamp = System.currentTimeMillis()
        val allDetections = mutableListOf<TrackedDetection>()
        
        // Run face detection
        val faces = faceDetector.detect(bitmap)
        faces.forEachIndexed { index, face ->
            allDetections.add(TrackedDetection(
                id = "face_${face.trackingId ?: index}_$timestamp",
                type = DetectionType.FACE,
                boundingBox = face.boundingBox,
                confidence = 0.9f,
                timestamp = timestamp,
                faceDetails = FaceDetails(
                    trackingId = face.trackingId,
                    leftEyeOpenProbability = face.leftEyeOpenProbability,
                    rightEyeOpenProbability = face.rightEyeOpenProbability,
                    smilingProbability = face.smilingProbability,
                    headEulerAngleX = face.headEulerAngleX,
                    headEulerAngleY = face.headEulerAngleY,
                    headEulerAngleZ = face.headEulerAngleZ,
                    landmarks = face.landmarks
                )
            ))
        }
        
        // Run object detection
        val objects: List<ObjectDetector.ObjectResult> = objectDetector.detect(bitmap)
        objects.forEachIndexed { index, obj ->
            val category = obj.category
            val detectionType = when (category) {
                ObjectDetector.DetectionCategory.PERSON -> DetectionType.PERSON
                ObjectDetector.DetectionCategory.PET -> DetectionType.PET
                ObjectDetector.DetectionCategory.PACKAGE -> DetectionType.PACKAGE
                ObjectDetector.DetectionCategory.VEHICLE -> DetectionType.VEHICLE
                else -> DetectionType.UNKNOWN
            }
            
            if (detectionType != DetectionType.UNKNOWN) {
                allDetections.add(TrackedDetection(
                    id = "obj_${obj.trackingId ?: index}_$timestamp",
                    type = detectionType,
                    boundingBox = obj.boundingBox,
                    confidence = obj.bestLabel?.confidence ?: 0.5f,
                    timestamp = timestamp,
                    objectDetails = ObjectDetails(
                        trackingId = obj.trackingId,
                        labels = obj.labels,
                        category = category
                    )
                ))
            }
        }
        
        // Update state on main thread
        CoroutineScope(Dispatchers.Main).launch {
            _detections.value = allDetections
            
            val faceCount = allDetections.count { it.type == DetectionType.FACE }
            val personCount = allDetections.count { it.type == DetectionType.PERSON }
            val petCount = allDetections.count { it.type == DetectionType.PET }
            val packageCount = allDetections.count { it.type == DetectionType.PACKAGE }
            val vehicleCount = allDetections.count { it.type == DetectionType.VEHICLE }
            
            _stats.value = DetectionStats(
                personCount = personCount,
                petCount = petCount,
                packageCount = packageCount,
                vehicleCount = vehicleCount,
                faceCount = faceCount,
                totalDetections = allDetections.size,
                lastUpdate = timestamp
            )
        }
        
        bitmap.recycle()
    }

    fun close() {
        stopTracking()
        faceDetector.close()
        objectDetector.close()
    }

    companion object {
        const val TAG = "DetectionManager"
    }
}