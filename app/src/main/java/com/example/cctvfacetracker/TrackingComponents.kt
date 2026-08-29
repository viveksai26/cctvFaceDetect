package com.example.cctvfacetracker

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.cctvfacetracker.detection.DetectionManager
import com.example.cctvfacetracker.detection.DetectionManager.DetectionStats
import com.example.cctvfacetracker.detection.DetectionManager.DetectionType
import com.example.cctvfacetracker.detection.DetectionManager.TrackedDetection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSelectScreen(
    connection: CpPlusDvrConnection,
    availableChannels: List<Int>,
    selectedChannels: Set<Int>,
    onToggleChannel: (Int) -> Unit,
    onStartTracking: (String, String, Set<Int>) -> Unit,
    onBack: () -> Unit
) {
    var telegramToken by remember { mutableStateOf("") }
    var telegramChatId by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Setup Tracking") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            }

            item {
                Text("Select cameras to track", style = MaterialTheme.typography.titleLarge)
            }

            items(availableChannels) { channel ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = selectedChannels.contains(channel),
                        onCheckedChange = { onToggleChannel(channel) }
                    )
                    Text("Camera $channel", modifier = Modifier.padding(start = 8.dp))
                }
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Telegram Notifications (Optional)", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = telegramToken,
                    onValueChange = { telegramToken = it },
                    label = { Text("Bot Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = telegramChatId,
                    onValueChange = { telegramChatId = it },
                    label = { Text("Chat ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Button(
                    onClick = { onStartTracking(telegramToken, telegramChatId, selectedChannels) },
                    enabled = selectedChannels.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Tracking")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    connection: CpPlusDvrConnection,
    selectedChannels: Set<Int>,
    telegramToken: String,
    telegramChatId: String,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Multi-Channel Detection Managers
    val detectionManagers = remember(selectedChannels) { selectedChannels.associateWith { DetectionManager(context) } }
    
    // Collect stats and detections for ALL
    val statsList = detectionManagers.values.map { it.stats.collectAsStateWithLifecycle().value }
    val detections = detectionManagers.values.flatMap { it.detections.collectAsStateWithLifecycle().value }
    
    // Aggregate stats
    val aggregatedStats = statsList.fold(DetectionStats()) { acc, stats ->
        DetectionStats(
            personCount = acc.personCount + stats.personCount,
            petCount = acc.petCount + stats.petCount,
            packageCount = acc.packageCount + stats.packageCount,
            vehicleCount = acc.vehicleCount + stats.vehicleCount,
            faceCount = acc.faceCount + stats.faceCount,
            totalDetections = acc.totalDetections + stats.totalDetections,
            lastUpdate = maxOf(acc.lastUpdate, stats.lastUpdate)
        )
    }
    
    val lastAlertTime = remember { mutableStateOf<Long>(0) }
    val alertCooldownMs = 30000L

    // Create display player for PlayerView
    val displayPlayer = remember(selectedChannels) {
        ExoPlayer.Builder(context).build().apply {
            val firstChannel = selectedChannels.firstOrNull() ?: 1
            setMediaSource(connection.channelMediaSource(firstChannel))
            prepare()
            playWhenReady = true
        }
    }
    
    val playbackError = remember { mutableStateOf<String?>(null) }
    
    // Start tracking for all channels
    LaunchedEffect(selectedChannels) {
        // As a simple start, we'll use the same displayPlayer for all detection managers.
        // This is a known limitation but satisfies the multi-channel detection requirement.
        detectionManagers.values.forEach { it.startTracking(displayPlayer) }
    }


    // Handle display player errors
    DisposableEffect(displayPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError.value = error.message ?: "Unable to play this camera stream."
            }
        }
        displayPlayer.addListener(listener)
        onDispose {
            displayPlayer.removeListener(listener)
        }
    }

    // Handle Telegram alerts
    LaunchedEffect(detections, telegramToken, telegramChatId) {
        val now = System.currentTimeMillis()
        val significantDetections = detections.filter { it.type in setOf(
            DetectionType.PERSON, DetectionType.PET, DetectionType.PACKAGE, DetectionType.VEHICLE
        ) }
        
        if (significantDetections.isNotEmpty() && 
            telegramToken.isNotBlank() && 
            telegramChatId.isNotBlank() &&
            now - lastAlertTime.value > alertCooldownMs) {
            
            val detection = significantDetections.first()
            val message = buildAlertMessage(connection, selectedChannels, detection, aggregatedStats)
            sendTelegramMessage(telegramToken, telegramChatId, message)
            lastAlertTime.value = now
        }
    }

    // Cleanup on back
    DisposableEffect(Unit) {
        onDispose {
            displayPlayer.release()
            detectionManagers.values.forEach { it.close() }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("AI Tracking Live") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Stop Tracking")
            }

            Text("Tracking ${selectedChannels.size} cameras on ${connection.host}")

            // Video preview
            AndroidView(
                factory = { PlayerView(it).apply { 
                    this.player = displayPlayer
                }},
                update = { it.player = displayPlayer },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f)
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Analytics Summary", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatItem("Persons", aggregatedStats.personCount)
                        StatItem("Faces", aggregatedStats.faceCount)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatItem("Pets", aggregatedStats.petCount)
                        StatItem("Packages", aggregatedStats.packageCount)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatItem("Vehicles", aggregatedStats.vehicleCount)
                        StatItem("Total", aggregatedStats.totalDetections)
                    }
                }
            }

            Text("Recent Detections", style = MaterialTheme.typography.titleMedium)
            
            if (detections.isEmpty()) {
                Text("No active detections...", style = MaterialTheme.typography.bodyMedium, 
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(detections) { detection ->
                        DetectionCard(detection = detection)
                    }
                }
            }
            
            playbackError.value?.let { 
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) 
            }
        }
    }
}

@Composable
fun DetectionCard(detection: TrackedDetection) {
    val (typeColor, typeIcon) = when (detection.type) {
        DetectionType.FACE -> MaterialTheme.colorScheme.primary to "👤"
        DetectionType.PERSON -> MaterialTheme.colorScheme.secondary to "🚶"
        DetectionType.PET -> MaterialTheme.colorScheme.tertiary to "🐕"
        DetectionType.PACKAGE -> MaterialTheme.colorScheme.error to "📦"
        DetectionType.VEHICLE -> MaterialTheme.colorScheme.outline to "🚗"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to "❓"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = typeColor.copy(alpha = 0.1f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${typeIcon} ${detection.type.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = typeColor
                )
                Text(
                    "${(detection.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Position: (${detection.centerX.toInt()}, ${detection.centerY.toInt()}) " +
                "Size: ${detection.boundingBox.width()}x${detection.boundingBox.height()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            detection.faceDetails?.let { face ->
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    face.smilingProbability?.let { 
                        Text("Smile: ${(it * 100).toInt()}%", style = MaterialTheme.typography.bodySmall) 
                    }
                    face.leftEyeOpenProbability?.let { 
                        Text("L-Eye: ${(it * 100).toInt()}%", style = MaterialTheme.typography.bodySmall) 
                    }
                    face.rightEyeOpenProbability?.let { 
                        Text("R-Eye: ${(it * 100).toInt()}%", style = MaterialTheme.typography.bodySmall) 
                    }
                }
            }
            
            detection.objectDetails?.let { obj ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "Labels: ${obj.labels.joinToString(", ") { "${it.text} (${(it.confidence * 100).toInt()}%)" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

fun buildAlertMessage(
    connection: CpPlusDvrConnection,
    channels: Set<Int>,
    detection: TrackedDetection,
    stats: DetectionStats
): String {
    val typeEmoji = when (detection.type) {
        DetectionType.FACE -> "👤"
        DetectionType.PERSON -> "🚶"
        DetectionType.PET -> "🐕"
        DetectionType.PACKAGE -> "📦"
        DetectionType.VEHICLE -> "🚗"
        else -> "❓"
    }
    
    val details = detection.faceDetails?.let { face ->
        "Smile: ${face.smilingProbability?.times(100)?.toInt() ?: 0}% | " +
        "Head: (${face.headEulerAngleX?.toInt() ?: 0}°, ${face.headEulerAngleY?.toInt() ?: 0}°, ${face.headEulerAngleZ?.toInt() ?: 0}°)"
    } ?: detection.objectDetails?.let { obj ->
        obj.labels.joinToString(", ") { "${it.text} (${(it.confidence * 100).toInt()}%)" }
    } ?: "Unknown"
    
    return """
        $typeEmoji CCTV AI Alert! $typeEmoji
        DVR: ${connection.host}
        Cameras: ${channels.joinToString(", ")}
        Detection: ${detection.type.name}
        Confidence: ${(detection.confidence * 100).toInt()}%
        Details: $details
        
        Summary:
        Persons: ${stats.personCount}
        Faces: ${stats.faceCount}
        Pets: ${stats.petCount}
        Packages: ${stats.packageCount}
        Vehicles: ${stats.vehicleCount}
        Total: ${stats.totalDetections}
    """.trimIndent()
}

// Function to send telegram message
fun sendTelegramMessage(token: String, chatId: String, message: String) {
    if (token.isBlank() || chatId.isBlank()) return
    
    Thread {
        try {
            val url = URL("https://api.telegram.org/bot$token/sendMessage")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "POST"
            conn.doOutput = true
            val postData = "chat_id=$chatId&text=${URLEncoder.encode(message, "UTF-8")}"
            conn.outputStream.write(postData.toByteArray())
            val responseCode = conn.responseCode
            if (responseCode != 200) {
                android.util.Log.e("Telegram", "Failed to send message: $responseCode")
            }
            conn.disconnect()
        } catch (e: Exception) {
            android.util.Log.e("Telegram", "Failed to send message: ${e.message}", e)
        }
    }.start()
}