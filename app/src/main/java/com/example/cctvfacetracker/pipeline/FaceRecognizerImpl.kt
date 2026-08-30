package com.example.cctvfacetracker.pipeline

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.Collections

class FaceRecognizerImpl(context: Context) : FaceRecognizer, AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelFile = copyModelToCache(context, "models/face_recognition_sface_2021dec.onnx")
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

    override fun extractEmbedding(alignedFace: Bitmap): FloatArray {
        val inputSize = 112
        val resized = Bitmap.createScaledBitmap(alignedFace, inputSize, inputSize, false)
        val floatBuffer = preprocess(resized)
        
        val inputName = session.inputNames.iterator().next()
        val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        
        val inputTensor = OnnxTensor.createTensor(env, floatBuffer, shape)
        
        inputTensor.use {
            session.run(Collections.singletonMap(inputName, it)).use { results ->
                val output = results[0].value as Array<FloatArray>
                return output[0]
            }
        }
    }

    private fun preprocess(bitmap: Bitmap): FloatBuffer {
        val size = 112
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

    override fun recognize(faceImage: Bitmap, enrolledEmbeddings: Map<String, FloatArray>): Pair<String, Float>? {
        val embedding = extractEmbedding(faceImage)
        var bestName = "Unknown"
        var bestSimilarity = -1f

        for ((name, enrolled) in enrolledEmbeddings) {
            val similarity = cosineSimilarity(embedding, enrolled)
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestName = name
            }
        }

        return if (bestSimilarity > 0.6f) Pair(bestName, bestSimilarity) else Pair("Unknown", bestSimilarity)
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return dot / (Math.sqrt(normA.toDouble()).toFloat() * Math.sqrt(normB.toDouble()).toFloat())
    }

    override fun close() {
        session.close()
    }
}
