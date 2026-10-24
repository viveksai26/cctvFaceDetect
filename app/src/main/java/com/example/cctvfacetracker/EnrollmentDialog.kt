package com.example.cctvfacetracker

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cctvfacetracker.pipeline.EmbeddingRepository
import com.example.cctvfacetracker.pipeline.FaceRecognizer
import android.graphics.Bitmap
import kotlinx.coroutines.launch

@Composable
fun EnrollmentDialog(
    faceImage: Bitmap,
    recognizer: FaceRecognizer,
    repository: EmbeddingRepository,
    suggestedName: String? = null,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(suggestedName ?: "") }
    var existingNames by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Load existing names so the user can add this as another sample of someone already
    // enrolled with one tap, instead of having to retype an exact-matching name from memory.
    LaunchedEffect(Unit) {
        repository.loadAll()
        existingNames = repository.getEnrolledEmbeddings().keys.sorted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enroll Face") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (suggestedName != null) {
                    Text(
                        "This looks similar to an existing \"$suggestedName\" -- confirm below, or pick/enter a different name.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
                if (existingNames.isNotEmpty()) {
                    Text("Or add another sample of:", style = MaterialTheme.typography.labelSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        for (existing in existingNames) {
                            FilterChip(
                                selected = name == existing,
                                onClick = { name = existing },
                                label = { Text(existing) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    scope.launch {
                        val embedding = recognizer.extractEmbedding(faceImage)
                        repository.enroll(name, embedding)
                        onDismiss()
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
