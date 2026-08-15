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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

private enum class AppScreen { DISCOVERY, CREDENTIALS, VALIDATING, CAMERAS, VIEWER }

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var screen by remember { mutableStateOf(AppScreen.DISCOVERY) }
                var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
                var connection by remember { mutableStateOf<CpPlusDvrConnection?>(null) }
                var selectedChannel by remember { mutableStateOf<Int?>(null) }
                CctvTrackerScreen(
                    state = state,
                    screen = screen,
                    selectedDevice = selectedDevice,
                    connection = connection,
                    selectedChannel = selectedChannel,
                    onRefreshNetwork = viewModel::refreshNetwork,
                    onScan = viewModel::startScan,
                    onCancel = viewModel::cancelScan,
                    onDeviceSelected = { device ->
                        selectedDevice = device
                        screen = AppScreen.CREDENTIALS
                    },
                    onCredentialsSubmitted = { dvrConnection ->
                        connection = dvrConnection
                        screen = AppScreen.VALIDATING
                    },
                    onCredentialsValidated = { screen = AppScreen.CAMERAS },
                    onCredentialFailure = { screen = AppScreen.CREDENTIALS },
                    onChannelSelected = { channel ->
                        selectedChannel = channel
                        screen = AppScreen.VIEWER
                    },
                    onBack = {
                        screen = when (screen) {
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
    connection: CpPlusDvrConnection?,
    selectedChannel: Int?,
    onRefreshNetwork: () -> Unit,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onDeviceSelected: (DiscoveredDevice) -> Unit,
    onCredentialsSubmitted: (CpPlusDvrConnection) -> Unit,
    onCredentialsValidated: () -> Unit,
    onCredentialFailure: () -> Unit,
    onChannelSelected: (Int) -> Unit,
    onBack: () -> Unit,
) {
    when (screen) {
        AppScreen.DISCOVERY -> DiscoveryScreen(state, onRefreshNetwork, onScan, onCancel, onDeviceSelected)
        AppScreen.CREDENTIALS -> selectedDevice?.let { CredentialsScreen(it, onCredentialsSubmitted, onBack) }
        AppScreen.VALIDATING -> connection?.let { CredentialValidationScreen(it, onCredentialsValidated, onCredentialFailure) }
        AppScreen.CAMERAS -> connection?.let { CameraListScreen(it, onChannelSelected, onBack) }
        AppScreen.VIEWER -> connection?.let { dvr -> selectedChannel?.let { CctvViewerScreen(dvr, it, onBack) } }
    }
}

@Composable
private fun DiscoveryScreen(
    state: ScannerUiState,
    onRefreshNetwork: () -> Unit,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onDeviceSelected: (DiscoveredDevice) -> Unit,
) {
    if (!state.isScanning && state.devices.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = onScan, enabled = state.network != null) { Text("Scan LAN") }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("CCTV Face Tracker", style = MaterialTheme.typography.headlineMedium)
            Text(state.network?.cidr ?: "No active Wi-Fi IPv4 network", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onScan, enabled = !state.isScanning && state.network != null) { Text("Scan LAN") }
                if (state.isScanning) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            if (state.isScanning) Text("Checked ${state.scannedHosts} hosts", modifier = Modifier.padding(top = 8.dp))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            TextButton(onClick = onRefreshNetwork) { Text("Refresh network") }
        }
        item { Text("Discovered devices (${state.devices.size})", style = MaterialTheme.typography.titleLarge) }
        items(state.devices, key = { it.address }) { device ->
            Card(modifier = Modifier.fillMaxWidth(), onClick = { onDeviceSelected(device) }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(device.address, style = MaterialTheme.typography.titleMedium)
                    Text("Open ports: ${device.openPorts.joinToString()}")
                    Text(if (device.isLikelyCctvOrNvr) "Likely CCTV / NVR" else "Possible web-managed device")
                }
            }
        }
    }
}

@Composable
private fun CredentialsScreen(
    device: DiscoveredDevice,
    onCredentialsSubmitted: (CpPlusDvrConnection) -> Unit,
    onBack: () -> Unit,
) {
    var dvrIpAddress by remember(device.address) { mutableStateOf(device.address) }
    var username by remember { mutableStateOf("") }
    // Credentials stay only in the current in-memory UI session.
    var password by remember { mutableStateOf("") }
    var rtspPort by remember { mutableStateOf("554") }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text("Back to devices") }
            Text("Connect to ${device.address}", style = MaterialTheme.typography.headlineSmall)
            Text("Enter your CP Plus DVR credentials. We will verify Camera 1 before listing all cameras.")
        }
        item {
            OutlinedTextField(
                value = dvrIpAddress,
                onValueChange = { dvrIpAddress = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("DVR IP address") },
                supportingText = { Text("Pre-filled from LAN discovery; edit if needed.") },
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
        }
        item {
            Button(onClick = {
                runCatching {
                    require(dvrIpAddress.isNotBlank()) { "Enter the DVR IP address." }
                    require(username.isNotBlank()) { "Enter a username." }
                    require(password.isNotBlank()) { "Enter a password." }
                    val port = rtspPort.toIntOrNull() ?: error("Enter a valid RTSP port.")
                    require(port in 1..65535) { "Enter a valid RTSP port." }
                    CpPlusDvrConnection(dvrIpAddress.trim(), port, username, password)
                }
                    .onSuccess(onCredentialsSubmitted)
                    .onFailure { error = it.message }
            }, modifier = Modifier.fillMaxWidth()) { Text("Verify and show cameras") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}
