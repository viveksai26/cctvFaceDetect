package com.example.cctvfacetracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cctvfacetracker.database.AnalyticsRepository
import com.example.cctvfacetracker.database.JsonEmbeddingRepository
import com.example.cctvfacetracker.database.LogRepository

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

@Composable
fun TrackingDashboardScreen(repository: JsonEmbeddingRepository, analytics: AnalyticsRepository, logs: LogRepository, onBack: () -> Unit) {
    val analyticsData = remember { mutableStateOf(analytics.getData()) }
    val logData = remember { mutableStateOf(logs.getLogs()) }

    Column(modifier = Modifier.padding(20.dp)) {
        Button(onClick = onBack) { Text("Stop Tracking") }
        Text("Tracking Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text("Total Detections: ${analyticsData.value.optInt("total_detections")}")
        
        Text("Recent Logs", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(logData.value.length()) { index ->
                val log = logData.value.getJSONObject(index)
                Text("${log.getString("time")}: ${log.getString("message")}")
            }
        }
    }
}
