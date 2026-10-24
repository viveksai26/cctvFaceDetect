package com.example.cctvfacetracker

import android.view.TextureView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.*
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import com.example.cctvfacetracker.database.AnalyticsRepository
import com.example.cctvfacetracker.database.JsonEmbeddingRepository
import com.example.cctvfacetracker.database.LogRepository
import com.example.cctvfacetracker.pipeline.FacePipeline

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

/** Holds the pipeline + its closeable ONNX sessions for the lifetime of the dashboard screen. */
private class TrackingPipeline(
    val detector: com.example.cctvfacetracker.pipeline.FaceDetectorImpl,
    val recognizer: com.example.cctvfacetracker.pipeline.FaceRecognizerImpl,
    val processor: com.example.cctvfacetracker.pipeline.FaceProcessor,
    val alertScope: CoroutineScope,
)

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

    // AnalyticsRepository/LogRepository.getData()/getLogs() each return a fresh snapshot copy,
    // so reassigning state here reliably triggers recomposition.
    fun refreshDashboardState() {
        analyticsData.value = analytics.getData()
        logData.value = logs.getLogs()
    }

    val pipeline = remember {
        val detector = com.example.cctvfacetracker.pipeline.FaceDetectorImpl(context)
        val tracker = com.example.cctvfacetracker.pipeline.FaceTrackerImpl()
        val recognizer = com.example.cctvfacetracker.pipeline.FaceRecognizerImpl(context)
        val processor = com.example.cctvfacetracker.pipeline.FaceProcessor(detector, tracker, recognizer, repository)
        val alertScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val alertEngine = com.example.cctvfacetracker.alert.AlertEngine(
            scope = alertScope,
            analyticsRepo = analytics,
            logRepo = logs
        )
        processor.setListener(alertEngine)
        TrackingPipeline(detector, recognizer, processor, alertScope)
    }

    LaunchedEffect(Unit) {
        logs.load()
        analytics.load()
        repository.loadAll()
        logs.addLog("Tracking started")
        for (channel in selectedTrackChannels) {
            logs.addLog("Camera $channel tracking started")
        }
        refreshDashboardState()
    }

    DisposableEffect(Unit) {
        onDispose {
            pipeline.detector.close()
            pipeline.recognizer.close()
            CoroutineScope(Dispatchers.IO).launch { logs.addLog("Tracking stopped") }
            pipeline.alertScope.cancel()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Stop Tracking") }

        Text("Tracking Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text("Total Detections: ${analyticsData.value.optInt("total_detections")}")

        Text("Live Feeds", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (channel in selectedTrackChannels) {
                key(channel) {
                    TrackedChannelFeed(
                        connection = connection,
                        channel = channel,
                        processor = pipeline.processor,
                        logs = logs,
                        onFrameProcessed = { refreshDashboardState() },
                    )
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

/**
 * Renders one tracked camera's stream into an off-screen-sized [TextureView] (ExoPlayer has no
 * direct bitmap-frame API) and periodically grabs a real frame from it to feed the pipeline,
 * replacing the previous dummy/blank-bitmap placeholder.
 */
@Composable
private fun TrackedChannelFeed(
    connection: CpPlusDvrConnection,
    channel: Int,
    processor: FacePipeline,
    logs: LogRepository,
    onFrameProcessed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val textureView = remember { TextureView(context) }
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }

    val player = remember(connection, channel) {
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(connection.channelMediaSource(channel))
            setVideoTextureView(textureView)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("TRACKING_RTSP", "RTSP error channel $channel: ${error.message}")
                scope.launch(Dispatchers.IO) { logs.addLog("Camera $channel tracking error: ${error.message}") }
            }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
                android.util.Log.d("TRACKING_RTSP", "TRACKING_RTSP_OPEN_SUCCESS channel=$channel size=${videoSize.width}x${videoSize.height}")
                scope.launch(Dispatchers.IO) { logs.addLog("TRACKING_RTSP_OPEN_SUCCESS channel=$channel") }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        delay(1000)
        while (isActive) {
            if (videoWidth > 0 && videoHeight > 0 && textureView.isAvailable) {
                val maxDimension = 960
                val scaleFactor = (maxDimension.toFloat() / maxOf(videoWidth, videoHeight)).coerceAtMost(1f)
                val captureWidth = (videoWidth * scaleFactor).toInt().coerceAtLeast(1)
                val captureHeight = (videoHeight * scaleFactor).toInt().coerceAtLeast(1)
                val frame = runCatching { textureView.getBitmap(captureWidth, captureHeight) }.getOrNull()
                if (frame != null) {
                    withContext(Dispatchers.Default) { processor.processFrame(frame) }
                    logs.addLog("Camera $channel frame processed (${captureWidth}x${captureHeight})")
                    onFrameProcessed()
                }
            }
            delay(2000)
        }
    }

    Column {
        Text("Camera $channel", style = MaterialTheme.typography.labelSmall)
        AndroidView(
            factory = { textureView },
            modifier = Modifier.size(width = 120.dp, height = 68.dp)
        )
    }
}
