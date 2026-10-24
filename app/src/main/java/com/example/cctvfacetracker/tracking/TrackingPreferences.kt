package com.example.cctvfacetracker.tracking

import android.content.Context

/**
 * Persists which DVR host + channels were actively being tracked, so [BootReceiver] can resume
 * tracking after a reboot. Only a host reference is stored here (not credentials); the full
 * connection is looked up from [com.example.cctvfacetracker.DvrCredentialStore] at boot time.
 */
class TrackingPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE)

    fun save(host: String, channels: Set<Int>) {
        prefs.edit()
            .putString(KEY_HOST, host)
            .putStringSet(KEY_CHANNELS, channels.map { it.toString() }.toSet())
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun load(): Pair<String, Set<Int>>? {
        val host = prefs.getString(KEY_HOST, null) ?: return null
        val channels = prefs.getStringSet(KEY_CHANNELS, emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()
        if (channels.isEmpty()) return null
        return host to channels
    }

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_CHANNELS = "channels"
    }
}
