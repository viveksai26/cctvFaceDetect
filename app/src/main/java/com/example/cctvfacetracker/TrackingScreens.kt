package com.example.cctvfacetracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.*
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.cctvfacetracker.database.AnalyticsRepository
import com.example.cctvfacetracker.database.JsonEmbeddingRepository
import com.example.cctvfacetracker.database.LogRepository
import com.example.cctvfacetracker.tracking.TrackingBus
import com.example.cctvfacetracker.tracking.TrackingService
import com.example.cctvfacetracker.tracking.UnknownSighting

@Composable
fun TrackingSetupScreen(
    connection: CpPlusDvrConnection,
    availableChannels: List<Int>,
    selectedTrackChannels: Set<Int>,
    onToggleTrackChannel: (Int) -> Unit,
    onStartTracking: (String, String, Set<Int>) -> Unit,
    onBack: () -> Unit
) {
    var botToken by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onBack) { Text("Back") }
        Text("Tracking Setup", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = botToken, onValueChange = { botToken = it }, label = { Text("Telegram Bot Token (Optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = chatId, onValueChange = { chatId = it }, label = { Text("Telegram Chat ID (Optional)") }, modifier = Modifier.fillMaxWidth())

        Text("Select Cameras to Track:", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(availableChannels) { channel ->
                val selected = selectedTrackChannels.contains(channel)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Camera $channel")
                    Checkbox(checked = selected, onCheckedChange = { onToggleTrackChannel(channel) })
                }
            }
        }

        Button(
            onClick = { onStartTracking(botToken, chatId, selectedTrackChannels) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Start Tracking") }
    }
}

/**
 * The pipeline itself runs headlessly in [TrackingService] (so it survives this screen, and the
 * app, being backgrounded). This screen just starts that service and reflects its state: logs
 * and analytics are read from the JSON files the service writes to, and live thumbnails / the
 * last unidentified face come from [TrackingBus], an in-process bridge to the running service.
 */
@Composable
fun TrackingDashboardScreen(
    connection: CpPlusDvrConnection,
    selectedTrackChannels: Set<Int>,
    telegramToken: String,
    telegramChatId: String,
    repository: JsonEmbeddingRepository,
    analytics: AnalyticsRepository,
    logs: LogRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val analyticsData = remember { mutableStateOf(analytics.getData()) }
    val logData = remember { mutableStateOf(logs.getLogs()) }
    val thumbnails by TrackingBus.channelThumbnails.collectAsState()
    val unknownSightings by TrackingBus.unknownSightings.collectAsState()
    var enrollingSighting by remember { mutableStateOf<UnknownSighting?>(null) }

    val enrollmentRecognizer = remember { com.example.cctvfacetracker.pipeline.FaceRecognizerImpl(context) }
    DisposableEffect(Unit) {
        onDispose { enrollmentRecognizer.close() }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* tracking proceeds either way; denial just means no system notifications */ }

    LaunchedEffect(Unit) {
        logs.load()
        analytics.load()
        analyticsData.value = analytics.getData()
        logData.value = logs.getLogs()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        ContextCompat.startForegroundService(
            context,
            TrackingService.startIntent(context, connection, selectedTrackChannels, telegramToken, telegramChatId)
        )

        // The service persists logs/analytics to the same JSON files this screen reads from;
        // poll periodically so this UI reflects what's happening in the background pipeline.
        while (isActive) {
            delay(2000)
            logs.load()
            analytics.load()
            logData.value = logs.getLogs()
            analyticsData.value = analytics.getData()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                context.startService(Intent(context, TrackingService::class.java).setAction(TrackingService.ACTION_STOP))
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Stop Tracking") }

        Text("Tracking Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text("Total Detections: ${analyticsData.value.optInt("total_detections")}")
        Text(
            "Tracking keeps running in the background — a notification stays visible while it's active.",
            style = MaterialTheme.typography.bodySmall
        )

        if (unknownSightings.isNotEmpty()) {
            Text("Unidentified Faces (${unknownSightings.size})", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(unknownSightings, key = { "${it.channel}:${it.trackId}" }) { sighting ->
                    Card {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Camera ${sighting.channel}", style = MaterialTheme.typography.labelSmall)
                            Image(
                                bitmap = sighting.image.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(80.dp)
                            )
                            sighting.nearestCandidate?.let {
                                Text("possibly $it", style = MaterialTheme.typography.labelSmall)
                            }
                            Row {
                                TextButton(onClick = { enrollingSighting = sighting }) { Text("Enroll") }
                                TextButton(onClick = { TrackingBus.dismissUnknownFace(sighting.channel, sighting.trackId) }) { Text("Dismiss") }
                            }
                        }
                    }
                }
            }
        }

        enrollingSighting?.let { sighting ->
            EnrollmentDialog(
                faceImage = sighting.image,
                recognizer = enrollmentRecognizer,
                repository = repository,
                suggestedName = sighting.nearestCandidate,
                onDismiss = {
                    TrackingBus.dismissUnknownFace(sighting.channel, sighting.trackId)
                    enrollingSighting = null
                }
            )
        }

        Text("Live Feeds", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (channel in selectedTrackChannels) {
                Column {
                    Text("Camera $channel", style = MaterialTheme.typography.labelSmall)
                    val bitmap = thumbnails[channel]
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(width = 120.dp, height = 68.dp)
                        )
                    } else {
                        Box(modifier = Modifier.size(width = 120.dp, height = 68.dp))
                    }
                }
            }
        }

        Text("Recent Logs", style = MaterialTheme.typography.titleMedium)
        val listState = rememberLazyListState()

        LaunchedEffect(logData.value.length()) {
            if (logData.value.length() > 0) {
                listState.animateScrollToItem(logData.value.length() - 1)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(logData.value.length()) { index ->
                val log = logData.value.getJSONObject(index)
                Text("[${log.getString("time")}] ${log.getString("message")}")
            }
        }
    }
}
