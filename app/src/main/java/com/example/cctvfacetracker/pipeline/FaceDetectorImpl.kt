package com.example.cctvfacetracker.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.Collections

class FaceDetectorImpl(context: Context) : FaceDetector, AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelFile = copyModelToCache(context, "models/face_detection_yunet_2023mar.onnx")
        session = env.createSession(modelFile.absolutePath)
    }

    private fun copyModelToCache(context: Context, assetPath: String): File {
        val file = File(context.cacheDir, assetPath.substringAfterLast("/"))
        if (!file.exists()) {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file
    }

    override fun detectFaces(frame: Bitmap): List<FaceResult> {
        val inputSize = 640
        val resized = Bitmap.createScaledBitmap(frame, inputSize, inputSize, false)
        val floatBuffer = preprocess(resized)

        val inputName = session.inputNames.iterator().next()
        val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        
        val inputTensor = OnnxTensor.createTensor(env, floatBuffer, shape)

        inputTensor.use {
            session.run(Collections.singletonMap(inputName, it)).use { results ->
                val decoded = decode(results, frame.width.toFloat() / inputSize, frame.height.toFloat() / inputSize)
                TrackingLog.d("TRACKING_YUNET", "TRACKING_YUNET RESULT detections=${decoded.size}")
                return decoded
            }
        }
    }

    private fun preprocess(bitmap: Bitmap): FloatBuffer {
        val size = 640
        val buffer = FloatBuffer.allocate(1 * 3 * size * size)
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        
        // YuNet (OpenCV Zoo) is trained on BGR, unnormalized [0,255] input
        // (cv2.dnn.blobFromImage with scalefactor=1, swapRB=false) — matching that here.
        for (c in 0 until 3) {
            for (i in 0 until size * size) {
                val pixel = pixels[i]
                val channelValue = when (c) {
                    0 -> pixel and 0xFF          // B
                    1 -> (pixel shr 8) and 0xFF  // G
                    else -> (pixel shr 16) and 0xFF // R
                }
                buffer.put(channelValue.toFloat())
            }
        }
        buffer.rewind()
        return buffer
    }

    private fun decode(results: OrtSession.Result, scaleX: Float, scaleY: Float): List<FaceResult> {
        val faces = mutableListOf<FaceResult>()
        val strides = intArrayOf(8, 16, 32)
        val scoreThreshold = 0.6f

        for (stride in strides) {
            val clsTensor = results.get("cls_$stride")
            val objTensor = results.get("obj_$stride")
            val bboxTensor = results.get("bbox_$stride")
            val kpsTensor = results.get("kps_$stride")
            
            if (!clsTensor.isPresent || !objTensor.isPresent || !bboxTensor.isPresent || !kpsTensor.isPresent) continue

            val cls = getArray(clsTensor.get() as OnnxTensor)
            val obj = getArray(objTensor.get() as OnnxTensor)
            val bbox = getArray(bboxTensor.get() as OnnxTensor)
            val kps = getArray(kpsTensor.get() as OnnxTensor)

            TrackingLog.d("TRACKING_YUNET_OUTPUT", "stride=$stride cls_size=${cls.size} obj_size=${obj.size} bbox_size=${bbox.size}")

            val gridSize = 640 / stride
            for (y in 0 until gridSize) {
                for (x in 0 until gridSize) {
                    val idx = (y * gridSize + x)
                    val score = yunetScore(cls[idx], obj[idx])
                    if (score < scoreThreshold) continue

                    val box = yunetDecodeBox(x, y, stride, bbox[idx * 4 + 0], bbox[idx * 4 + 1], bbox[idx * 4 + 2], bbox[idx * 4 + 3])

                    val rect = RectF(
                        (box.cx - box.w / 2) * scaleX,
                        (box.cy - box.h / 2) * scaleY,
                        (box.cx + box.w / 2) * scaleX,
                        (box.cy + box.h / 2) * scaleY
                    )

                    val points = Array(5) { i ->
                        val (px, py) = yunetDecodePoint(x, y, stride, kps[idx * 10 + i * 2 + 0], kps[idx * 10 + i * 2 + 1])
                        PointF(px * scaleX, py * scaleY)
                    }

                    faces.add(FaceResult(rect, score, points[0], points[1], points[2], points[3], points[4]))
                }
            }
        }
        return nms(faces, 0.3f)
    }

    private fun getArray(tensor: OnnxTensor): FloatArray {
        val buffer = tensor.floatBuffer
        val array = FloatArray(buffer.remaining())
        buffer.get(array)
        return array
    }

    private fun nms(faces: List<FaceResult>, iouThreshold: Float): List<FaceResult> {
        if (faces.isEmpty()) return emptyList()
        val sorted = faces.sortedByDescending { it.confidence }
        val kept = mutableListOf<FaceResult>()
        val removed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (removed[i]) continue
            kept.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (removed[j]) continue
                if (iou(sorted[i].boundingBox, sorted[j].boundingBox) > iouThreshold) {
                    removed[j] = true
                }
            }
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val intersection = RectF()
        if (!intersection.setIntersect(a, b)) return 0f
        val intersectionArea = intersection.width() * intersection.height()
        val aArea = a.width() * a.height()
        val bArea = b.width() * b.height()
        return intersectionArea / (aArea + bArea - intersectionArea)
    }

    override fun close() {
        session.close()
    }
}
