package com.example.cctvfacetracker.database

import android.content.Context
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnalyticsRepository(context: Context) {
    private val file = File(context.filesDir, "analytics.json")
    private var data = JSONObject()
    private val lock = Any()

    suspend fun load() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (file.exists()) data = JSONObject(file.readText())
        }
    }

    // Returns a snapshot copy: callers can read safely while increment() concurrently
    // mutates the backing object on another thread.
    fun getData(): JSONObject = synchronized(lock) { JSONObject(data.toString()) }

    suspend fun increment(key: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = data.optInt(key, 0)
            data.put(key, current + 1)
            file.writeText(data.toString())
        }
    }
}
