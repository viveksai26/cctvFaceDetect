package com.example.cctvfacetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cctvfacetracker.network.CctvNetworkScanner

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                CctvTrackerScreen(
                    state = state,
                    onRefreshNetwork = viewModel::refreshNetwork,
                    onScan = viewModel::startScan,
                    onCancel = viewModel::cancelScan,
                )
            }
        }
    }
}

@Composable
private fun CctvTrackerScreen(
    state: ScannerUiState,
    onRefreshNetwork: () -> Unit,
    onScan: () -> Unit,
    onCancel: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    // Credentials stay only in the current in-memory UI session.
    var password by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("CCTV Face Tracker", style = MaterialTheme.typography.headlineMedium)
            Text("Phase 1 · Local network discovery", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Local Wi-Fi network", style = MaterialTheme.typography.titleMedium)
                    Text(state.network?.cidr ?: "No active Wi-Fi IPv4 network", modifier = Modifier.padding(top = 4.dp))
                    TextButton(onClick = onRefreshNetwork) { Text("Refresh network") }
                }
            }
        }
        item {
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
                visualTransformation = if (password.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
            )
        }
        item {
            Row {
                Button(onClick = onScan, enabled = !state.isScanning && state.network != null) { Text("Scan LAN") }
                Spacer(Modifier.width(8.dp))
                if (state.isScanning) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(modifier = Modifier.height(24.dp).width(24.dp), strokeWidth = 2.dp)
                }
            }
            if (state.isScanning) Text("Checked ${state.scannedHosts} hosts", modifier = Modifier.padding(top = 8.dp))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        }
        item {
            Text("Discovered devices (${state.devices.size})", style = MaterialTheme.typography.titleLarge)
            Text("Ports are checked only within this Wi-Fi subnet. Up to ${CctvNetworkScanner.MAX_HOSTS} hosts are scanned.")
        }
        items(state.devices, key = { it.address }) { device ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(device.address, style = MaterialTheme.typography.titleMedium)
                    Text("Open ports: ${device.openPorts.joinToString()}")
                    Text(if (device.isLikelyCctvOrNvr) "Likely CCTV / NVR" else "Possible web-managed device")
                }
            }
        }
    }
}
