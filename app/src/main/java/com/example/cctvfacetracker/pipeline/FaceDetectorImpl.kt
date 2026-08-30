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
                return decode(results, frame.width.toFloat() / inputSize, frame.height.toFloat() / inputSize)
            }
        }
    }

    private fun preprocess(bitmap: Bitmap): FloatBuffer {
        val size = 640
        val buffer = FloatBuffer.allocate(1 * 3 * size * size)
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        
        for (c in 0 until 3) {
            for (i in 0 until size * size) {
                val pixel = pixels[i]
                val channelValue = when (c) {
                    0 -> (pixel shr 16) and 0xFF // R
                    1 -> (pixel shr 8) and 0xFF  // G
                    else -> pixel and 0xFF       // B
                }
                buffer.put(channelValue.toFloat() / 255.0f)
            }
        }
        buffer.rewind()
        return buffer
    }

    private fun decode(results: OrtSession.Result, scaleX: Float, scaleY: Float): List<FaceResult> {
        val faces = mutableListOf<FaceResult>()
        val strides = intArrayOf(8, 16, 32)
        val scoreThreshold = 0.5f

        for (stride in strides) {
            val cls = getArray(results.get("cls_$stride").get() as OnnxTensor)
            val obj = getArray(results.get("obj_$stride").get() as OnnxTensor)
            val bbox = getArray(results.get("bbox_$stride").get() as OnnxTensor)
            val kps = getArray(results.get("kps_$stride").get() as OnnxTensor)

            val gridSize = 640 / stride
            for (y in 0 until gridSize) {
                for (x in 0 until gridSize) {
                    val idx = (y * gridSize + x)
                    val score = sigmoid(cls[idx]) * sigmoid(obj[idx])
                    if (score < scoreThreshold) continue

                    val cx = (x + 0.5f) * stride + bbox[idx * 4 + 0] * stride
                    val cy = (y + 0.5f) * stride + bbox[idx * 4 + 1] * stride
                    val w = Math.exp(bbox[idx * 4 + 2].toDouble()).toFloat() * stride
                    val h = Math.exp(bbox[idx * 4 + 3].toDouble()).toFloat() * stride

                    val rect = RectF(
                        (cx - w / 2) * scaleX,
                        (cy - h / 2) * scaleY,
                        (cx + w / 2) * scaleX,
                        (cy + h / 2) * scaleY
                    )

                    val points = Array(5) { i ->
                        PointF(
                            ((x + 0.5f) * stride + kps[idx * 10 + i * 2 + 0] * stride) * scaleX,
                            ((y + 0.5f) * stride + kps[idx * 10 + i * 2 + 1] * stride) * scaleY
                        )
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

    private fun sigmoid(x: Float): Float = 1.0f / (1.0f + Math.exp(-x.toDouble()).toFloat())

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
