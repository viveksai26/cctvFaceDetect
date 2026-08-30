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

    suspend fun load() = withContext(Dispatchers.IO) {
        if (file.exists()) logs = JSONObject(file.readText()).getJSONArray("logs")
    }

    fun getLogs(): JSONArray = logs

    suspend fun addLog(message: String) = withContext(Dispatchers.IO) {
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
