package com.example.cctvfacetracker

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.cctvfacetracker.database.RoomEmbeddingRepository
import com.example.cctvfacetracker.pipeline.FaceRecognizer
import android.graphics.Bitmap
import kotlinx.coroutines.launch

@Composable
fun EnrollmentDialog(
    faceImage: Bitmap,
    recognizer: FaceRecognizer,
    repository: RoomEmbeddingRepository,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enroll Face") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true
            )
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
