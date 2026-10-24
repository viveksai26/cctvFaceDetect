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

class FaceRecognizerImpl(
    context: Context,
    private val matchThreshold: Float = DEFAULT_MATCH_THRESHOLD,
    private val nearMissThreshold: Float = DEFAULT_NEAR_MISS_THRESHOLD,
) : FaceRecognizer, AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        session = try {
            val modelFile = copyModelToCache(context, "models/face_recognition_sface_2021dec.onnx")
            env.createSession(modelFile.absolutePath)
        } catch (e: Exception) {
            android.util.Log.e("FaceRecognizerImpl", "SFace model load failed: ${e.message}", e)
            throw e
        }
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
        
        // SFace is trained on RGB, unnormalized [0,255] input
        // (cv2.dnn.blobFromImage with scalefactor=1, swapRB=true) — matching that here.
        for (c in 0 until 3) {
            for (i in 0 until size * size) {
                val pixel = pixels[i]
                val channelValue = when (c) {
                    0 -> (pixel shr 16) and 0xFF // R
                    1 -> (pixel shr 8) and 0xFF  // G
                    else -> pixel and 0xFF       // B
                }
                buffer.put(channelValue.toFloat())
            }
        }
        buffer.rewind()
        return buffer
    }

    override fun recognize(faceImage: Bitmap, enrolledEmbeddings: Map<String, List<FloatArray>>): RecognitionOutcome {
        val embedding = extractEmbedding(faceImage)
        var bestName = "Unknown"
        var bestSimilarity = -1f

        // Each name may have several enrolled samples (different sightings/poses); match
        // against whichever sample is closest, since any one of them confirms the identity.
        for ((name, samples) in enrolledEmbeddings) {
            val bestForName = samples.maxOfOrNull { cosineSimilarity(embedding, it) } ?: continue
            if (bestForName > bestSimilarity) {
                bestSimilarity = bestForName
                bestName = name
            }
        }

        return when {
            bestSimilarity > matchThreshold -> RecognitionOutcome(bestName, bestSimilarity)
            bestSimilarity > nearMissThreshold -> RecognitionOutcome("Unknown", bestSimilarity, nearestCandidate = bestName)
            else -> RecognitionOutcome("Unknown", bestSimilarity)
        }
    }

    override fun close() {
        session.close()
    }

    companion object {
        /** Cosine-similarity cutoff for a match; tune per deployment lighting/camera/distance. */
        const val DEFAULT_MATCH_THRESHOLD = 0.6f

        /** Below this, a sighting isn't similar enough to any enrolled name to suggest one. */
        const val DEFAULT_NEAR_MISS_THRESHOLD = 0.4f
    }
}
