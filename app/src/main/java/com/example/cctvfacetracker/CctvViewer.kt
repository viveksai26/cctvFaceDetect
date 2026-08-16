package com.example.cctvfacetracker

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume

data class CpPlusDvrConnection(val host: String, val rtspPort: Int, val username: String, val password: String, val numCameras: Int) {
    fun channelUri(channel: Int): String {
        require(channel in 1..numCameras)
        return Uri.Builder().scheme("rtsp")
            .encodedAuthority("${Uri.encode(Uri.decode(username))}:${Uri.encode(Uri.decode(password))}@$host:$rtspPort")
            .appendPath("cam").appendPath("realmonitor")
            .appendQueryParameter("channel", channel.toString())
            // CP Plus sub-stream is typically H.264 and more broadly supported by Media3.
            .appendQueryParameter("subtype", "1")
            .build().toString()
    }

    @Suppress("UnsafeOptInUsageError")
    fun channelMediaSource(channel: Int) = RtspMediaSource.Factory()
        .setForceUseRtpTcp(true)
        .createMediaSource(MediaItem.fromUri(channelUri(channel)))
}

@Composable
fun CredentialValidationScreen(connection: CpPlusDvrConnection, onVerified: (List<Int>) -> Unit, onFailed: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(connection) {
        withContext(Dispatchers.IO) {
            try {
                // Verify TCP connectivity to the RTSP port
                val socket = Socket()
                socket.connect(InetSocketAddress(connection.host, connection.rtspPort), 5000)
                socket.close()
                
                withContext(Dispatchers.Main) {
                    onVerified((1..connection.numCameras).toList())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = "Failed to connect to DVR on port ${connection.rtspPort}: ${e.message}"
                }
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (error == null) {
            CircularProgressIndicator(Modifier.size(36.dp))
            Text("Verifying DVR connectivity...")
        } else {
            Text("Could not verify the DVR", style = MaterialTheme.typography.headlineSmall)
            Text(error!!, color = MaterialTheme.colorScheme.error)
            Button(onClick = onFailed) { Text("Try again") }
        }
    }
}

@Composable
fun CameraListScreen(connection: CpPlusDvrConnection, availableChannels: List<Int>, onChannelSelected: (Int) -> Unit, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(50.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to devices", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(16.dp))
            Text("${connection.host} cameras", style = MaterialTheme.typography.headlineSmall)
            Text("Found ${availableChannels.size} active cameras.")
        }
        items(availableChannels) { channel ->
            Card(onClick = { onChannelSelected(channel) }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text("Camera $channel", style = MaterialTheme.typography.titleLarge)
                    Text("Open live view", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun CctvViewerScreen(connection: CpPlusDvrConnection, channel: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    var playbackError by remember { mutableStateOf<String?>(null) }
    val player = remember(connection, channel) {
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(connection.channelMediaSource(channel))
            prepare(); playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { playbackError = error.message ?: "Unable to play this camera stream." }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    Column(Modifier.fillMaxSize().padding(50.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("All cameras", style = MaterialTheme.typography.titleMedium) }
        Text("Camera $channel", style = MaterialTheme.typography.headlineSmall)
        AndroidView(factory = { PlayerView(it).apply { this.player = player } }, update = { it.player = player }, modifier = Modifier.fillMaxWidth().weight(1f))
        playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
