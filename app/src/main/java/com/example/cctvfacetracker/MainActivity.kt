package com.example.cctvfacetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cctvfacetracker.network.DiscoveredDevice

private enum class AppScreen { DISCOVERY, CREDENTIALS, VALIDATING, CAMERAS, VIEWER, TRACK_SELECT, TRACKING }

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var screen by remember { mutableStateOf(AppScreen.DISCOVERY) }
                var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
                var editingConnection by remember { mutableStateOf<CpPlusDvrConnection?>(null) }
                var connection by remember { mutableStateOf<CpPlusDvrConnection?>(null) }
                var availableChannels by remember { mutableStateOf<List<Int>>(emptyList()) }
                var selectedChannel by remember { mutableStateOf<Int?>(null) }
                var selectedTrackChannels by remember { mutableStateOf<Set<Int>>(emptySet()) }
                var telegramToken by remember { mutableStateOf("") }
                var telegramChatId by remember { mutableStateOf("") }
                
                // For simplicity, just auto-connect to the first saved DVR if present,
                // or let the user choose from the list.
                val firstSaved = state.savedConnections.firstOrNull()
                LaunchedEffect(firstSaved) {
                    if (connection == null && firstSaved != null) {
                        connection = CpPlusDvrConnection(
                            firstSaved.host,
                            firstSaved.rtspPort,
                            firstSaved.username,
                            firstSaved.password,
                            firstSaved.numCameras
                        )
                        availableChannels = (1..firstSaved.numCameras).toList()
                        // Don't auto-navigate to CAMERAS if we just want to stay in DISCOVERY
                        // screen = AppScreen.CAMERAS
                    }
                }


                CctvTrackerScreen(
                    state = state,
                    screen = screen,
                    selectedDevice = selectedDevice,
                    editingConnection = editingConnection,
                    connection = connection,
                    selectedChannel = selectedChannel,
                    availableChannels = connection?.let { (1..it.numCameras).toList() } ?: emptyList(),
                    selectedTrackChannels = selectedTrackChannels,
                    telegramToken = telegramToken,
                    telegramChatId = telegramChatId,
                    onScan = viewModel::startScan,
                    onCancel = viewModel::cancelScan,
                    onDeviceSelected = { device ->
                        selectedDevice = device
                        editingConnection = null
                        screen = AppScreen.CREDENTIALS
                    },
                    onManualAdd = {
                        selectedDevice = null
                        editingConnection = null
                        screen = AppScreen.CREDENTIALS
                    },
                    onCredentialsSubmitted = { dvrConnection ->
                        connection = dvrConnection
                        screen = AppScreen.VALIDATING
                    },
                    onCredentialsValidated = { channels ->
                        connection?.let { 
                            viewModel.saveConnection(it)
                            availableChannels = (1..it.numCameras).toList()
                        }
                        screen = AppScreen.CAMERAS
                    },
                    onCredentialFailure = { screen = AppScreen.CREDENTIALS },
                    onDeleteSavedConnection = viewModel::deleteConnection,
                    onViewSavedConnection = { conn ->
                        connection = conn
                        availableChannels = (1..conn.numCameras).toList()
                        screen = AppScreen.CAMERAS
                    },
                    onTrackSavedConnection = { conn ->
                        connection = conn
                        availableChannels = (1..conn.numCameras).toList()
                        selectedTrackChannels = emptySet()
                        screen = AppScreen.TRACK_SELECT
                    },
                    onEditSavedConnection = { conn ->
                        editingConnection = conn
                        screen = AppScreen.CREDENTIALS
                    },
                    onChannelSelected = { channel ->
                        selectedChannel = channel
                        screen = AppScreen.VIEWER
                    },
                    onToggleTrackChannel = { channel ->
                        selectedTrackChannels = if (selectedTrackChannels.contains(channel)) {
                            selectedTrackChannels - channel
                        } else {
                            selectedTrackChannels + channel
                        }
                    },
                    onStartTracking = { token, chatId ->
                        telegramToken = token
                        telegramChatId = chatId
                        screen = AppScreen.TRACKING
                    },
                    onBack = {
                        screen = when (screen) {
                            AppScreen.TRACKING -> AppScreen.TRACK_SELECT
                            AppScreen.TRACK_SELECT -> AppScreen.DISCOVERY
                            AppScreen.VIEWER -> AppScreen.CAMERAS
                            AppScreen.CAMERAS, AppScreen.VALIDATING, AppScreen.CREDENTIALS -> AppScreen.DISCOVERY
                            AppScreen.DISCOVERY -> AppScreen.DISCOVERY
                        }
                    },
                )
            }
        }
    }
}
@Composable
private fun CctvTrackerScreen(
    state: ScannerUiState,
    screen: AppScreen,
    selectedDevice: DiscoveredDevice?,
    editingConnection: CpPlusDvrConnection?,
    connection: CpPlusDvrConnection?,
    selectedChannel: Int?,
    availableChannels: List<Int>,
    selectedTrackChannels: Set<Int>,
    telegramToken: String,
    telegramChatId: String,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onDeviceSelected: (DiscoveredDevice) -> Unit,
    onManualAdd: () -> Unit,
    onCredentialsSubmitted: (CpPlusDvrConnection) -> Unit,
    onCredentialsValidated: (List<Int>) -> Unit,
    onCredentialFailure: () -> Unit,
    onDeleteSavedConnection: (String) -> Unit,
    onViewSavedConnection: (CpPlusDvrConnection) -> Unit,
    onTrackSavedConnection: (CpPlusDvrConnection) -> Unit,
    onEditSavedConnection: (CpPlusDvrConnection) -> Unit,
    onChannelSelected: (Int) -> Unit,
    onToggleTrackChannel: (Int) -> Unit,
    onStartTracking: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    when (screen) {
        AppScreen.DISCOVERY -> DiscoveryScreen(state, onScan, onCancel, onDeviceSelected, onManualAdd, onDeleteSavedConnection, onViewSavedConnection, onTrackSavedConnection, onEditSavedConnection)
        AppScreen.CREDENTIALS -> CredentialsScreen(selectedDevice, editingConnection, onCredentialsSubmitted, onBack)
        AppScreen.VALIDATING -> connection?.let { CredentialValidationScreen(it, onCredentialsValidated, onCredentialFailure) }
        AppScreen.CAMERAS -> connection?.let { CameraListScreen(it, availableChannels, onChannelSelected, onBack) }
        AppScreen.VIEWER -> connection?.let { dvr -> selectedChannel?.let { CctvViewerScreen(dvr, it, onBack) } }
        AppScreen.TRACK_SELECT -> connection?.let { TrackSelectScreen(it, availableChannels, selectedTrackChannels, onToggleTrackChannel, onStartTracking, onBack) }
        AppScreen.TRACKING -> connection?.let { TrackingScreen(it, selectedTrackChannels, telegramToken, telegramChatId, onBack) }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryScreen(
    state: ScannerUiState,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onDeviceSelected: (DiscoveredDevice) -> Unit,
    onManualAdd: () -> Unit,
    onDeleteSavedConnection: (String) -> Unit,
    onViewSavedConnection: (CpPlusDvrConnection) -> Unit,
    onTrackSavedConnection: (CpPlusDvrConnection) -> Unit,
    onEditSavedConnection: (CpPlusDvrConnection) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("CCTV Viewer And Tracker") }) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(state.network?.cidr ?: "No active Wi-Fi IPv4 network", style = MaterialTheme.typography.bodyMedium) }
            
            if (state.savedConnections.isNotEmpty()) {
                item { Text("Saved DVRs", style = MaterialTheme.typography.titleLarge) }
                items(state.savedConnections, key = { it.id }) { saved ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(saved.host, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onViewSavedConnection(CpPlusDvrConnection(saved.host, saved.rtspPort, saved.username, saved.password, saved.numCameras)) }) { Text("View") }
                            TextButton(onClick = { onTrackSavedConnection(CpPlusDvrConnection(saved.host, saved.rtspPort, saved.username, saved.password, saved.numCameras)) }) { Text("Track") }
                            TextButton(onClick = { onEditSavedConnection(CpPlusDvrConnection(saved.host, saved.rtspPort, saved.username, saved.password, saved.numCameras)) }) { Text("Edit") }
                            TextButton(onClick = { onDeleteSavedConnection(saved.id) }) { Text("Delete") }
                        }
                    }
                }
            }
            
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onScan, enabled = !state.isScanning && state.network != null, modifier = Modifier.weight(1f)) { 
                        Text("Scan LAN Cameras")
                    }
                    Button(onClick = onManualAdd, modifier = Modifier.weight(1f)) { 
                        Text("Manual Add via IP")
                    }
                }
                if (state.isScanning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Scanning...")
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onCancel) { Text("Cancel") }
                    }
                    Text("Checked ${state.scannedHosts} hosts", modifier = Modifier.padding(top = 8.dp))
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
            // ... (rest)
        }
    }
}

@Composable
private fun CredentialsScreen(
    device: DiscoveredDevice?,
    initialConnection: CpPlusDvrConnection?,
    onCredentialsSubmitted: (CpPlusDvrConnection) -> Unit,
    onBack: () -> Unit,
) {
    var dvrIpAddress by remember(device?.address, initialConnection) { mutableStateOf(device?.address ?: initialConnection?.host ?: "") }
    var username by remember(initialConnection) { mutableStateOf(initialConnection?.username ?: "") }
    // Credentials stay only in the current in-memory UI session.
    var password by remember(initialConnection) { mutableStateOf(initialConnection?.password ?: "") }
    var rtspPort by remember(initialConnection) { mutableStateOf((initialConnection?.rtspPort ?: 554).toString()) }
    var numCameras by remember(initialConnection) { mutableStateOf((initialConnection?.numCameras ?: 8).toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(50.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to devices", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(16.dp))
            Text(if (device != null) "Connect to ${device.address}" else "Manual Connection", style = MaterialTheme.typography.headlineSmall)
            Text("Enter your CP Plus DVR credentials. We will verify Camera 1 before listing all cameras.")
        }
        item {
            OutlinedTextField(
                value = dvrIpAddress,
                onValueChange = { dvrIpAddress = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("DVR IP address") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("CCTV username") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("CCTV password") },
                supportingText = { Text("Enter the password itself, e.g. admin@123.") },
                visualTransformation = if (password.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = rtspPort,
                onValueChange = { rtspPort = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("RTSP port") },
                supportingText = { Text("CP Plus default: 554") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = numCameras,
                onValueChange = { numCameras = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Number of cameras") },
                singleLine = true,
            )
        }
        item {
            Button(onClick = {
                runCatching {
                    require(dvrIpAddress.isNotBlank()) { "Enter the DVR IP address." }
                    require(username.isNotBlank()) { "Enter a username." }
                    require(password.isNotBlank()) { "Enter a password." }
                    val port = rtspPort.toIntOrNull() ?: error("Enter a valid RTSP port.")
                    require(port in 1..65535) { "Enter a valid RTSP port." }
                    val num = numCameras.toIntOrNull() ?: error("Enter a valid number of cameras.")
                    require(num in 1..64) { "Enter a valid number of cameras." }
                    CpPlusDvrConnection(dvrIpAddress.trim(), port, username, password, num)
                }
                    .onSuccess(onCredentialsSubmitted)
                    .onFailure { error = it.message }
            }, modifier = Modifier.fillMaxWidth()) { Text("Verify and show cameras") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}
