package com.example.cctvfacetracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cctvfacetracker.network.CctvNetworkScanner
import com.example.cctvfacetracker.network.DiscoveredDevice
import com.example.cctvfacetracker.network.LocalNetworkInfo
import com.example.cctvfacetracker.network.LocalNetworkInfoProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScannerUiState(
    val network: LocalNetworkInfo? = null,
    val isScanning: Boolean = false,
    val scannedHosts: Int = 0,
    val devices: List<DiscoveredDevice> = emptyList(),
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val networkProvider = LocalNetworkInfoProvider(application)
    private val scanner = CctvNetworkScanner()
    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState = _uiState.asStateFlow()
    private var scanJob: Job? = null

    init {
        refreshNetwork()
    }

    fun refreshNetwork() {
        val network = networkProvider.current()
        _uiState.update {
            it.copy(network = network, error = if (network == null) "Connect to a Wi-Fi network to scan its LAN." else null)
        }
    }

    fun startScan() {
        val network = networkProvider.current()
        if (network == null) {
            _uiState.update { it.copy(network = null, error = "Connect to a Wi-Fi network to scan its LAN.") }
            return
        }
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.value = ScannerUiState(network = network, isScanning = true)
            scanner.scan(network)
                .catch { error -> _uiState.update { it.copy(isScanning = false, error = error.message ?: "Scan failed.") } }
                .collect { device ->
                    _uiState.update { state ->
                        state.copy(
                            scannedHosts = state.scannedHosts + 1,
                            devices = if (device == null) state.devices else (state.devices + device).sortedBy { it.address },
                        )
                    }
                }
        }.also { job ->
            job.invokeOnCompletion { _uiState.update { it.copy(isScanning = false) } }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }
}
