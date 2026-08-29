package com.example.cctvfacetracker.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class FrameExtractor(private val context: Context) {

    private var frameChannel: Channel<Bitmap>? = null
    private var isRunning = false
    private val frameIntervalMs = 2000L
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var eglConfig: EGLConfig? = null
    private var textureId = -1
    private var program = -1
    private var vertexBuffer: FloatBuffer? = null
    private var textureBuffer: FloatBuffer? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var outputWidth = 640
    private var outputHeight = 480
    private val stMatrix = FloatArray(16)

    private val vertexShaderCode = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        attribute vec4 aPosition;
        attribute vec4 aTextureCoord;
        varying vec2 vTextureCoord;
        uniform mat4 uSTMatrix;
        void main() {
            gl_Position = aPosition;
            vTextureCoord = (uSTMatrix * aTextureCoord).xy;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform samplerExternalOES sTexture;
        void main() {
            gl_FragColor = texture2D(sTexture, vTextureCoord);
        }
    """.trimIndent()

    fun start(player: ExoPlayer): ReceiveChannel<Bitmap> {
        if (isRunning) {
            stop()
        }
        
        frameChannel = Channel(10)
        
        val videoSize = player.videoSize
        outputWidth = videoSize?.width ?: 640
        outputHeight = videoSize?.height ?: 480
        
        // Limit output size for performance
        if (outputWidth > 1280 || outputHeight > 720) {
            val scale = minOf(1280f / outputWidth, 720f / outputHeight)
            outputWidth = (outputWidth * scale).toInt()
            outputHeight = (outputHeight * scale).toInt()
        }
        
        initEgl()
        createTexture()
        setupShader()
        createVertexBuffers()
        surfaceTexture = createSurfaceTexture()
        val inputSurface = Surface(surfaceTexture!!)
        
        // Set player to render to our surface
        player.setVideoSurface(inputSurface)
        
        isRunning = true
        
        CoroutineScope(Dispatchers.IO).launch {
            while (isRunning) {
                try {
                    delay(frameIntervalMs)
                    captureFrame()
                } catch (e: Exception) {
                    Log.e("FrameExtractor", "Frame capture error", e)
                }
            }
        }
        
        return frameChannel!!
    }

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)
        
        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE
        )
        val configs = Array(1) { null as EGLConfig? }
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
        eglConfig = configs[0]
        
        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig!!, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        
        val surfaceAttribs = intArrayOf(
            EGL14.EGL_WIDTH, outputWidth,
            EGL14.EGL_HEIGHT, outputHeight,
            EGL14.EGL_NONE
        )
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig!!, surfaceAttribs, 0)
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    private fun createTexture() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun setupShader() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e("FrameExtractor", "Shader link failed: ${GLES20.glGetProgramInfoLog(program)}")
        }
        
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e("FrameExtractor", "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun createVertexBuffers() {
        val vertices = floatArrayOf(
            -1f, -1f, 0f,  // bottom left
            1f, -1f, 0f,   // bottom right
            -1f, 1f, 0f,   // top left
            1f, 1f, 0f     // top right
        )
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(vertices); position(0) }
        
        val textureCoords = floatArrayOf(
            0f, 1f,  // bottom left
            1f, 1f,  // bottom right
            0f, 0f,  // top left
            1f, 0f   // top right
        )
        textureBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(textureCoords); position(0) }
    }

    private fun createSurfaceTexture(): SurfaceTexture {
        val surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setDefaultBufferSize(outputWidth, outputHeight)
        return surfaceTexture
    }

    private fun captureFrame() {
        surfaceTexture?.updateTexImage()
        surfaceTexture?.getTransformMatrix(stMatrix)
        
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        
        GLES20.glUseProgram(program)
        
        val aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        val aTextureCoord = GLES20.glGetAttribLocation(program, "aTextureCoord")
        val uSTMatrix = GLES20.glGetUniformLocation(program, "uSTMatrix")
        val sTexture = GLES20.glGetUniformLocation(program, "sTexture")
        
        vertexBuffer?.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer!!)
        GLES20.glEnableVertexAttribArray(aPosition)
        
        textureBuffer?.position(0)
        GLES20.glVertexAttribPointer(aTextureCoord, 2, GLES20.GL_FLOAT, false, 8, textureBuffer!!)
        GLES20.glEnableVertexAttribArray(aTextureCoord)
        
        GLES20.glUniformMatrix4fv(uSTMatrix, 1, false, stMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(sTexture, 0)
        
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        
        val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val buffer = ByteBuffer.allocate(outputWidth * outputHeight * 4)
        buffer.order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, outputWidth, outputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        bitmap.copyPixelsFromBuffer(buffer)
        
        // Flip bitmap vertically (OpenGL origin is bottom-left)
        val flippedBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(flippedBitmap)
        canvas.scale(1f, -1f, outputWidth / 2f, outputHeight / 2f)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        bitmap.recycle()
        
        frameChannel?.trySend(flippedBitmap)
    }

    fun stop() {
        isRunning = false
        
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            eglSurface = EGL14.EGL_NO_SURFACE
        }
        if (eglContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            eglContext = EGL14.EGL_NO_CONTEXT
        }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
        
        if (textureId != -1) {
            val textures = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, textures, 0)
            textureId = -1
        }
        
        if (program != -1) {
            GLES20.glDeleteProgram(program)
            program = -1
        }
        
        surfaceTexture?.release()
        surfaceTexture = null
        
        frameChannel?.close()
        frameChannel = null
    }

    companion object {
        const val TAG = "FrameExtractor"
    }
}