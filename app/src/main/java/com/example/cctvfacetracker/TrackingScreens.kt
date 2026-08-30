package com.example.cctvfacetracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cctvfacetracker.database.AnalyticsRepository
import com.example.cctvfacetracker.database.JsonEmbeddingRepository
import com.example.cctvfacetracker.database.LogRepository

@Composable
fun TrackingSetupScreen(savedConnections: List<SavedDvrCredentials>, onBack: () -> Unit, onConfirm: () -> Unit) {
    var botToken by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }
    
    Column(modifier = Modifier.padding(20.dp)) {
        Button(onClick = onBack) { Text("Back") }
        Text("Tracking Setup", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = botToken, onValueChange = { botToken = it }, label = { Text("Telegram Bot Token") })
        OutlinedTextField(value = chatId, onValueChange = { chatId = it }, label = { Text("Telegram Chat ID") })
        Button(onClick = {
            if (botToken.isNotBlank() && chatId.isNotBlank()) {
                // Save config to shared prefs here
                onConfirm()
            }
        }) { Text("Save & Start Tracking") }
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
