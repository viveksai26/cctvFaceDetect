package com.example.cctvfacetracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random

data class TrackingStats(
    val personCount: Int = 0,
    val petCount: Int = 0,
    val packageCount: Int = 0,
    val visitorCount: Int = 0,
    val detections: List<PersonDetection> = emptyList()
)

data class PersonDetection(
    val name: String? = null,
    val age: Int? = null,
    val mood: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSelectScreen(
    connection: CpPlusDvrConnection,
    availableChannels: List<Int>,
    selectedChannels: Set<Int>,
    onToggleChannel: (Int) -> Unit,
    onStartTracking: (String, String) -> Unit,
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
                    onClick = { onStartTracking(telegramToken, telegramChatId) },
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
    var stats by remember { mutableStateOf(TrackingStats()) }
    val scope = rememberCoroutineScope()

    // Simulated AI Tracking Logic
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000) // Update every 5 seconds
            val newDetections = if (Random.nextInt(10) > 6) {
                val moods = listOf("Happy", "Neutral", "Serious", "Angry")
                val names = listOf("John Doe", "Jane Smith", "Unknown Visitor", "Package Delivery")
                listOf(PersonDetection(
                    name = names.random(),
                    age = Random.nextInt(18, 65),
                    mood = moods.random()
                ))
            } else {
                emptyList()
            }

            val pCount = if (newDetections.isNotEmpty()) 1 else 0
            val petC = if (Random.nextInt(10) > 8) 1 else 0
            val packC = if (Random.nextInt(20) > 18) 1 else 0
            val visC = if (Random.nextInt(15) > 13) 1 else 0

            stats = stats.copy(
                personCount = stats.personCount + pCount,
                petCount = stats.petCount + petC,
                packageCount = stats.packageCount + packC,
                visitorCount = stats.visitorCount + visC,
                detections = newDetections
            )

            if (newDetections.isNotEmpty() && telegramToken.isNotBlank() && telegramChatId.isNotBlank()) {
                val detection = newDetections.first()
                val message = """
                    🚨 CCTV AI Alert! 🚨
                    DVR: ${connection.host}
                    Cameras: ${selectedChannels.joinToString()}
                    Detection: ${detection.name}
                    Age: ${detection.age}
                    Mood: ${detection.mood}
                    
                    Summary:
                    Persons: ${stats.personCount}
                    Pets: ${stats.petCount}
                    Packages: ${stats.packageCount}
                    Visitors: ${stats.visitorCount}
                """.trimIndent()
                sendTelegramMessage(telegramToken, telegramChatId, message)
            }
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

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Analytics Summary", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatItem("Persons", stats.personCount)
                        StatItem("Pets", stats.petCount)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatItem("Packages", stats.packageCount)
                        StatItem("Visitors", stats.visitorCount)
                    }
                }
            }

            Text("Recent Detections", style = MaterialTheme.typography.titleMedium)
            
            if (stats.detections.isEmpty()) {
                Text("No active detections...")
            } else {
                stats.detections.forEach { detection ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Name: ${detection.name ?: "Unknown"}", style = MaterialTheme.typography.bodyLarge)
                            Text("Age: ${detection.age ?: "N/A"}", style = MaterialTheme.typography.bodyMedium)
                            Text("Mood: ${detection.mood ?: "Neutral"}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
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

// Function to send telegram message (can be called from TrackingScreen effect)
fun sendTelegramMessage(token: String, chatId: String, message: String) {
    if (token.isBlank() || chatId.isBlank()) return
    
    Thread {
        try {
            val url = URL("https://api.telegram.org/bot$token/sendMessage")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            val postData = "chat_id=$chatId&text=${URLEncoder.encode(message, "UTF-8")}"
            conn.outputStream.write(postData.toByteArray())
            conn.inputStream.read()
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }.start()
}
