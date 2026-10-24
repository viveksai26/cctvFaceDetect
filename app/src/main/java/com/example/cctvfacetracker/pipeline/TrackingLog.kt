package com.example.cctvfacetracker.pipeline

import android.util.Log
import com.example.cctvfacetracker.BuildConfig

/** Thin Log.d wrapper gated on BuildConfig.DEBUG, so hot-path diagnostic logging is silent in release builds. */
object TrackingLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }
}
