package com.example.cctvfacetracker.tracking

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * One unidentified face currently pending enrollment, deduped by (channel, trackId).
 * [nearestCandidate] is set when the sighting was a near-miss against an already-enrolled name,
 * so the enrollment UI can suggest "is this already them?" instead of a blank name field.
 */
data class UnknownSighting(
    val channel: Int,
    val trackId: Int,
    val image: Bitmap,
    val nearestCandidate: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * In-process bridge between [TrackingService] (which owns the actual capture/inference
 * pipeline and has no UI of its own) and the Compose dashboard observing it. Safe because the
 * service and the activity always run in the same process.
 */
object TrackingBus {
    val channelThumbnails = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())

    /** Pending unidentified faces, most recent last, capped at [MAX_PENDING]. */
    val unknownSightings = MutableStateFlow<List<UnknownSighting>>(emptyList())

    val isRunning = MutableStateFlow(false)

    fun updateThumbnail(channel: Int, bitmap: Bitmap) {
        channelThumbnails.value = channelThumbnails.value + (channel to bitmap)
    }

    fun reportUnknownFace(channel: Int, trackId: Int, image: Bitmap, nearestCandidate: String? = null) {
        val withoutStaleSighting = unknownSightings.value.filterNot { it.channel == channel && it.trackId == trackId }
        unknownSightings.value = (withoutStaleSighting + UnknownSighting(channel, trackId, image, nearestCandidate)).takeLast(MAX_PENDING)
    }

    fun dismissUnknownFace(channel: Int, trackId: Int) {
        unknownSightings.value = unknownSightings.value.filterNot { it.channel == channel && it.trackId == trackId }
    }

    fun reset() {
        channelThumbnails.value = emptyMap()
        unknownSightings.value = emptyList()
    }

    private const val MAX_PENDING = 5
}
