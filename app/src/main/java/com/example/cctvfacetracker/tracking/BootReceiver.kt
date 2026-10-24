package com.example.cctvfacetracker.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.cctvfacetracker.CpPlusDvrConnection
import com.example.cctvfacetracker.DvrCredentialStore

/** Resumes tracking after a reboot if it was active when the device shut down. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val (host, channels) = TrackingPreferences(context).load() ?: return
        val saved = DvrCredentialStore(context).loadAll().firstOrNull { it.host == host } ?: return
        val connection = CpPlusDvrConnection(saved.host, saved.rtspPort, saved.username, saved.password, saved.numCameras)

        ContextCompat.startForegroundService(context, TrackingService.startIntent(context, connection, channels))
    }
}
