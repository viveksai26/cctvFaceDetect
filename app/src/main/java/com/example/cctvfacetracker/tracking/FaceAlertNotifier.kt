package com.example.cctvfacetracker.tracking

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.cctvfacetracker.pipeline.PipelineFaceResult
import com.example.cctvfacetracker.pipeline.PipelineListener
import java.util.concurrent.ConcurrentHashMap

/** Posts a system notification per (channel, name), respecting a per-key cooldown. */
class FaceAlertNotifier(
    private val context: Context,
    private val channel: Int,
) : PipelineListener {
    private val lastNotifiedAt = ConcurrentHashMap<String, Long>()

    override fun onFaceDetected(face: PipelineFaceResult) {
        // Notification is posted once recognition resolves a name; nothing to do here.
    }

    override fun onRecognitionResult(name: String, confidence: Float, image: Bitmap) {
        if (name == "Unknown") {
            TrackingBus.lastUnknownFace.value = image
        }

        val now = System.currentTimeMillis()
        val key = "$channel:$name"
        if (now - (lastNotifiedAt[key] ?: 0L) < COOLDOWN_MS) return
        lastNotifiedAt[key] = now

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val title = if (name == "Unknown") "Unknown face detected" else "$name recognized"
        val notification = NotificationCompat.Builder(context, TrackingService.CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText("Camera $channel")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setLargeIcon(image)
            .setStyle(NotificationCompat.BigPictureStyle().bigPicture(image))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_BASE + (key.hashCode() and 0xFFFF), notification)
    }

    private companion object {
        const val COOLDOWN_MS = 60_000L
        const val NOTIFICATION_ID_BASE = 2000
    }
}
