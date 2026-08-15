package com.example.cctvfacetracker.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

data class LocalNetworkInfo(
    val address: Inet4Address,
    val prefixLength: Int,
) {
    val cidr: String = "${address.hostAddress}/$prefixLength"
}

/** Finds the IPv4 address assigned to the currently active Wi-Fi LAN. */
class LocalNetworkInfoProvider(private val context: Context) {
    fun current(): LocalNetworkInfo? {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        return connectivityManager.getLinkProperties(activeNetwork)
            ?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
            ?.let { LocalNetworkInfo(it.address as Inet4Address, it.prefixLength) }
    }
}
