package com.example.cctvfacetracker.alert

import android.graphics.Bitmap
import com.example.cctvfacetracker.BuildConfig
import com.example.cctvfacetracker.network.TelegramSender
import com.example.cctvfacetracker.pipeline.PipelineFaceResult
import com.example.cctvfacetracker.pipeline.PipelineListener
import com.example.cctvfacetracker.database.AnalyticsRepository
import com.example.cctvfacetracker.database.LogRepository
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.Date

class AlertEngine(
    private val scope: CoroutineScope,
    private val analyticsRepo: AnalyticsRepository,
    private val logRepo: LogRepository,
    telegramToken: String? = null,
    telegramChatId: String? = null,
) : PipelineListener {
    // Per-session credentials (entered on the Tracking Setup screen) win when present;
    // otherwise fall back to whatever was baked into BuildConfig at build time.
    private val telegramSender = TelegramSender(
        telegramToken?.takeIf { it.isNotBlank() } ?: BuildConfig.TELEGRAM_BOT_TOKEN,
        telegramChatId?.takeIf { it.isNotBlank() } ?: BuildConfig.TELEGRAM_CHAT_ID,
    )
    private val lastAlertTime = ConcurrentHashMap<String, Long>()
    private val ALERT_COOLDOWN = 60_000L // 1 minute

    override fun onFaceDetected(face: PipelineFaceResult) {
        scope.launch {
            analyticsRepo.increment("total_detections")
            logRepo.addLog("Face detected")
        }
    }

    override fun onRecognitionResult(trackId: Int, name: String, confidence: Float, nearestCandidate: String?, image: Bitmap) {
        scope.launch {
            if (name == "Unknown") {
                analyticsRepo.increment("unknown_detections")
                logRepo.addLog(if (nearestCandidate != null) "Unknown face (possibly $nearestCandidate)" else "Unknown face")
            } else {
                analyticsRepo.increment("known_detections")
                logRepo.addLog("$name recognized")
            }
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - (lastAlertTime[name] ?: 0L) < ALERT_COOLDOWN) return
        lastAlertTime[name] = currentTime

        scope.launch {
            val caption = "Face: $name\nDate/time: ${Date()}"
            val sent = telegramSender.sendAlert(image, caption)
            logRepo.addLog(if (sent) "Telegram alert sent for $name" else "Telegram alert failed for $name")
        }
    }
}
