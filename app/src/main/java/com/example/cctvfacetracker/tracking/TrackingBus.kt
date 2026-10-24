package com.example.cctvfacetracker.tracking

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-process bridge between [TrackingService] (which owns the actual capture/inference
 * pipeline and has no UI of its own) and the Compose dashboard observing it. Safe because the
 * service and the activity always run in the same process.
 */
object TrackingBus {
    val channelThumbnails = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val lastUnknownFace = MutableStateFlow<Bitmap?>(null)
    val isRunning = MutableStateFlow(false)

    fun updateThumbnail(channel: Int, bitmap: Bitmap) {
        channelThumbnails.value = channelThumbnails.value + (channel to bitmap)
    }

    fun clearUnknownFace() {
        lastUnknownFace.value = null
    }

    fun reset() {
        channelThumbnails.value = emptyMap()
        lastUnknownFace.value = null
    }
}
