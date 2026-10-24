package com.example.cctvfacetracker.database

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogRepository(context: Context) {
    private val file = File(context.filesDir, "logs.json")
    private var logs = JSONArray()
    private val lock = Any()

    suspend fun load() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (file.exists()) logs = JSONObject(file.readText()).getJSONArray("logs")
        }
    }

    // Returns a snapshot copy: callers (e.g. multiple tracked-camera coroutines) can read
    // safely while addLog() concurrently mutates the backing array on another thread.
    fun getLogs(): JSONArray = synchronized(lock) { JSONArray(logs.toString()) }

    suspend fun addLog(message: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val log = JSONObject()
            log.put("time", time)
            log.put("message", message)
            logs.put(log)
            val json = JSONObject()
            json.put("logs", logs)
            file.writeText(json.toString())
        }
    }
}
