package com.example.cctvfacetracker

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.cctvfacetracker.database.JsonEmbeddingRepository
import kotlinx.coroutines.launch

/** Lists enrolled names with their photo and sample count, and lets you rename or delete them. */
@Composable
fun EnrolledFacesScreen(
    repository: JsonEmbeddingRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var faces by remember { mutableStateOf<Map<String, List<FloatArray>>>(emptyMap()) }
    var renamingName by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    fun refresh() {
        faces = repository.getEnrolledEmbeddings()
    }

    LaunchedEffect(Unit) {
        repository.loadAll()
        refresh()
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onBack) { Text("Back") }
        Text("Enrolled Faces", style = MaterialTheme.typography.headlineMedium)

        if (faces.isEmpty()) {
            Text("No faces enrolled yet. Enroll one from the Tracking Dashboard when an unidentified face is spotted.")
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(faces.entries.toList(), key = { it.key }) { (name, samples) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val thumbnail = repository.getThumbnail(name)
                        if (thumbnail != null) {
                            Image(
                                bitmap = thumbnail.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                            )
                        } else {
                            Box(modifier = Modifier.size(56.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${samples.size} sample${if (samples.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { renamingName = name; renameText = name }) { Text("Rename") }
                        TextButton(onClick = { scope.launch { repository.delete(name); refresh() } }) { Text("Delete") }
                    }
                }
            }
        }
    }

    val currentRenaming = renamingName
    if (currentRenaming != null) {
        AlertDialog(
            onDismissRequest = { renamingName = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameText.trim()
                    if (newName.isNotBlank() && newName != currentRenaming) {
                        scope.launch { repository.rename(currentRenaming, newName); refresh() }
                    }
                    renamingName = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renamingName = null }) { Text("Cancel") } },
        )
    }
}
