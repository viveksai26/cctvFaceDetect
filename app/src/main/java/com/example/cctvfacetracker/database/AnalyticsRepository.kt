package com.example.cctvfacetracker.database

import android.content.Context
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnalyticsRepository(context: Context) {
    private val file = File(context.filesDir, "analytics.json")
    private var data = JSONObject()

    suspend fun load() = withContext(Dispatchers.IO) {
        if (file.exists()) data = JSONObject(file.readText())
    }

    fun getData(): JSONObject = data

    suspend fun increment(key: String) = withContext(Dispatchers.IO) {
        val current = data.optInt(key, 0)
        data.put(key, current + 1)
        file.writeText(data.toString())
    }
}
