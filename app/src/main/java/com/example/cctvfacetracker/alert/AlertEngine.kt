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
    private val logRepo: LogRepository
) : PipelineListener {
    private val telegramSender = TelegramSender(BuildConfig.TELEGRAM_BOT_TOKEN, BuildConfig.TELEGRAM_CHAT_ID)
    private val lastAlertTime = ConcurrentHashMap<String, Long>()
    private val ALERT_COOLDOWN = 60_000L // 1 minute

    override fun onFaceDetected(face: PipelineFaceResult) {
        scope.launch {
            analyticsRepo.increment("total_detections")
            logRepo.addLog("Face detected")
        }
    }

    override fun onRecognitionResult(name: String, confidence: Float, image: Bitmap) {
        scope.launch {
            if (name == "Unknown") {
                analyticsRepo.increment("unknown_detections")
                logRepo.addLog("Unknown face")
            } else {
                analyticsRepo.increment("known_detections")
                logRepo.addLog("\$name recognized")
            }
        }
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - (lastAlertTime[name] ?: 0L) < ALERT_COOLDOWN) return
        lastAlertTime[name] = currentTime

        scope.launch {
            val caption = "Face: $name\nDate/time: ${Date()}"
            telegramSender.sendAlert(image, caption)
            logRepo.addLog("Telegram alert sent for $name")
        }
    }
}
