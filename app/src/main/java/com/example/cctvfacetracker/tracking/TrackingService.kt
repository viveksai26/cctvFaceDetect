package com.example.cctvfacetracker.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.example.cctvfacetracker.CpPlusDvrConnection
import com.example.cctvfacetracker.MainActivity
import com.example.cctvfacetracker.alert.AlertEngine
import com.example.cctvfacetracker.database.AnalyticsRepository
import com.example.cctvfacetracker.database.JsonEmbeddingRepository
import com.example.cctvfacetracker.database.LogRepository
import com.example.cctvfacetracker.pipeline.CompositeListener
import com.example.cctvfacetracker.pipeline.FaceDetectorImpl
import com.example.cctvfacetracker.pipeline.FaceProcessor
import com.example.cctvfacetracker.pipeline.FaceRecognizerImpl
import com.example.cctvfacetracker.pipeline.FaceTrackerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs the detection/tracking/recognition pipeline as a foreground service so it survives the
 * Activity being backgrounded or killed. Captures frames headlessly via [ImageReader] (an
 * ExoPlayer video output surface backed by a buffer, not a UI [android.view.TextureView]) since
 * a Service has no window to attach a view to.
 */
class TrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var running = false

    private lateinit var logRepository: LogRepository
    private lateinit var analyticsRepository: AnalyticsRepository
    private lateinit var embeddingRepository: JsonEmbeddingRepository
    private lateinit var alertEngine: AlertEngine

    private class ChannelResources(
        val player: ExoPlayer,
        var imageReader: ImageReader,
        val captureThread: HandlerThread,
        val detector: FaceDetectorImpl,
        val recognizer: FaceRecognizerImpl,
    )

    private val channelResources = mutableMapOf<Int, ChannelResources>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID_STATUS, buildStatusNotification(0, 0))

        if (running) return START_STICKY

        val host = intent?.getStringExtra(EXTRA_HOST)
        val channels = intent?.getIntArrayExtra(EXTRA_CHANNELS) ?: IntArray(0)
        if (host == null || channels.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        running = true

        val connection = CpPlusDvrConnection(
            host,
            intent.getIntExtra(EXTRA_PORT, 554),
            intent.getStringExtra(EXTRA_USERNAME) ?: "",
            intent.getStringExtra(EXTRA_PASSWORD) ?: "",
            intent.getIntExtra(EXTRA_NUM_CAMERAS, 1),
        )

        logRepository = LogRepository(this)
        analyticsRepository = AnalyticsRepository(this)
        embeddingRepository = JsonEmbeddingRepository(this)
        alertEngine = AlertEngine(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            analyticsRepo = analyticsRepository,
            logRepo = logRepository,
        )

        serviceScope.launch {
            logRepository.load()
            analyticsRepository.load()
            embeddingRepository.loadAll()
            logRepository.addLog("Tracking started")
            for (channel in channels) {
                logRepository.addLog("Camera $channel tracking started")
                startChannel(connection, channel)
            }
            TrackingBus.isRunning.value = true
            refreshEmbeddingsPeriodically()
            refreshStatusNotificationPeriodically(channels.size)
        }

        return START_STICKY
    }

    private fun refreshEmbeddingsPeriodically() {
        serviceScope.launch {
            while (running) {
                delay(10_000)
                runCatching { embeddingRepository.loadAll() }
            }
        }
    }

    private fun refreshStatusNotificationPeriodically(channelCount: Int) {
        serviceScope.launch {
            while (running) {
                val total = analyticsRepository.getData().optInt("total_detections")
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID_STATUS, buildStatusNotification(channelCount, total))
                delay(5_000)
            }
        }
    }

    private fun startChannel(connection: CpPlusDvrConnection, channel: Int) {
        val captureThread = HandlerThread("capture-$channel").apply { start() }
        val captureHandler = Handler(captureThread.looper)

        val detector = FaceDetectorImpl(this)
        val tracker = FaceTrackerImpl()
        val recognizer = FaceRecognizerImpl(this)
        val processor = FaceProcessor(detector, tracker, recognizer, embeddingRepository)
        val notifier = FaceAlertNotifier(this, channel)
        processor.setListener(CompositeListener(listOf(alertEngine, notifier)))

        var lastProcessedAt = 0L

        fun attachImageListener(reader: ImageReader) {
            reader.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                val now = SystemClock.elapsedRealtime()
                if (now - lastProcessedAt < FRAME_INTERVAL_MS) {
                    image.close()
                    return@setOnImageAvailableListener
                }
                lastProcessedAt = now
                val bitmap = try {
                    imageToBitmap(image)
                } catch (e: Exception) {
                    null
                } finally {
                    image.close()
                }
                if (bitmap == null) return@setOnImageAvailableListener
                TrackingBus.updateThumbnail(channel, bitmap)
                // Runs synchronously on this channel's dedicated capture thread -- fine, it's
                // already off the main thread and naturally serializes this channel's frames.
                processor.processFrame(bitmap)
                serviceScope.launch {
                    logRepository.addLog("Camera $channel frame processed (${bitmap.width}x${bitmap.height})")
                }
            }, captureHandler)
        }

        val imageReader = ImageReader.newInstance(DEFAULT_WIDTH, DEFAULT_HEIGHT, ImageFormat.YUV_420_888, 2)
        attachImageListener(imageReader)

        val resources = ChannelResources(
            player = ExoPlayer.Builder(this).build(),
            imageReader = imageReader,
            captureThread = captureThread,
            detector = detector,
            recognizer = recognizer,
        )
        channelResources[channel] = resources

        resources.player.apply {
            setMediaSource(connection.channelMediaSource(channel))
            setVideoSurface(imageReader.surface)
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    serviceScope.launch { logRepository.addLog("Camera $channel tracking error: ${error.message}") }
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    val w = videoSize.width
                    val h = videoSize.height
                    val current = resources.imageReader
                    if (w <= 0 || h <= 0 || (current.width == w && current.height == h)) return
                    val newReader = ImageReader.newInstance(w, h, ImageFormat.YUV_420_888, 2)
                    attachImageListener(newReader)
                    setVideoSurface(newReader.surface)
                    resources.imageReader = newReader
                    current.close()
                    serviceScope.launch { logRepository.addLog("Camera $channel resolution ${w}x$h") }
                }
            })
            prepare()
            playWhenReady = true
        }
    }

    // Hardware video decoders write to the Surface in a GPU/tiled layout that a raw
    // Bitmap.copyPixelsFromBuffer() memcpy cannot safely read (crashes with SIGSEGV) even when
    // the ImageReader is configured as RGBA_8888. YUV_420_888 is the format actually guaranteed
    // to be CPU-readable for decoder output, so read it plane-by-plane and convert manually.
    private fun imageToBitmap(image: Image): Bitmap {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val pixels = IntArray(width * height)
        for (row in 0 until height) {
            val yRowStart = row * yRowStride
            val uvRowStart = (row / 2) * uvRowStride
            for (col in 0 until width) {
                val y = yBuffer.get(yRowStart + col * yPixelStride).toInt() and 0xFF
                val uvCol = col / 2
                val u = (uBuffer.get(uvRowStart + uvCol * uvPixelStride).toInt() and 0xFF) - 128
                val v = (vBuffer.get(uvRowStart + uvCol * uvPixelStride).toInt() and 0xFF) - 128

                val r = (y + 1.370705f * v).toInt().coerceIn(0, 255)
                val g = (y - 0.337633f * u - 0.698001f * v).toInt().coerceIn(0, 255)
                val b = (y + 1.732446f * u).toInt().coerceIn(0, 255)
                pixels[row * width + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun stopTracking() {
        val wasRunning = running
        running = false
        TrackingBus.isRunning.value = false
        for ((_, resources) in channelResources) {
            resources.player.release()
            resources.imageReader.close()
            resources.captureThread.quitSafely()
            resources.detector.close()
            resources.recognizer.close()
        }
        channelResources.clear()
        TrackingBus.reset()
        if (wasRunning && ::logRepository.isInitialized) {
            serviceScope.launch { logRepository.addLog("Tracking stopped") }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running) stopTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildStatusNotification(cameraCount: Int, totalDetections: Int): Notification {
        createChannels()
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (cameraCount > 0) "Watching $cameraCount camera(s) · $totalDetections detections" else "Starting…"
        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setContentTitle("CCTV tracking active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(contentIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "Tracking status", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Face alerts", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    companion object {
        const val ACTION_STOP = "com.example.cctvfacetracker.action.STOP_TRACKING"
        private const val EXTRA_HOST = "extra_host"
        private const val EXTRA_PORT = "extra_port"
        private const val EXTRA_USERNAME = "extra_username"
        private const val EXTRA_PASSWORD = "extra_password"
        private const val EXTRA_NUM_CAMERAS = "extra_num_cameras"
        private const val EXTRA_CHANNELS = "extra_channels"

        const val CHANNEL_STATUS = "tracking_status"
        const val CHANNEL_ALERTS = "face_alerts"
        const val NOTIFICATION_ID_STATUS = 1001

        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 480
        private const val FRAME_INTERVAL_MS = 2000L

        fun startIntent(context: Context, connection: CpPlusDvrConnection, channels: Set<Int>): Intent =
            Intent(context, TrackingService::class.java).apply {
                putExtra(EXTRA_HOST, connection.host)
                putExtra(EXTRA_PORT, connection.rtspPort)
                putExtra(EXTRA_USERNAME, connection.username)
                putExtra(EXTRA_PASSWORD, connection.password)
                putExtra(EXTRA_NUM_CAMERAS, connection.numCameras)
                putExtra(EXTRA_CHANNELS, channels.toIntArray())
            }
    }
}
