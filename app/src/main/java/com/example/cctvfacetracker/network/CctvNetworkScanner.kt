package com.example.cctvfacetracker.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class DiscoveredDevice(
    val address: String,
    val openPorts: Set<Int>,
) {
    val isLikelyCctvOrNvr: Boolean
        get() = openPorts.any { it in CCTV_SIGNATURE_PORTS }
}

/**
 * Performs TCP discovery only for addresses in the active Wi-Fi subnet.
 * It does not resolve or contact any address outside that subnet.
 */
class CctvNetworkScanner {
    fun scan(network: LocalNetworkInfo): Flow<DiscoveredDevice?> = channelFlow {
        coroutineScope {
            val semaphore = Semaphore(MAX_CONCURRENT_HOSTS)
            candidateAddresses(network).forEach { address ->
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        send(probe(address))
                    }
                }
            }
        }
    }

    private fun probe(address: Inet4Address): DiscoveredDevice? {
        val openPorts = CCTV_PORTS.filterTo(linkedSetOf()) { port ->
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                }
            }.isSuccess
        }
        return openPorts.takeIf { it.isNotEmpty() }
            ?.let { DiscoveredDevice(address.hostAddress.orEmpty(), it) }
    }

    private fun candidateAddresses(network: LocalNetworkInfo): Sequence<Inet4Address> = sequence {
        val prefix = network.prefixLength
        if (prefix !in MIN_SUPPORTED_PREFIX..30) return@sequence

        val hostBits = 32 - prefix
        val hostMask = (1L shl hostBits) - 1L
        val addressValue = network.address.address.fold(0L) { value, byte ->
            (value shl 8) or (byte.toLong() and 0xff)
        }
        val networkValue = addressValue and hostMask.inv() and IPV4_MASK
        val hostCount = minOf(hostMask - 1, MAX_HOSTS.toLong())

        for (offset in 1..hostCount) {
            val value = networkValue + offset
            val bytes = byteArrayOf(
                (value shr 24).toByte(),
                (value shr 16).toByte(),
                (value shr 8).toByte(),
                value.toByte(),
            )
            val candidate = InetAddress.getByAddress(bytes) as Inet4Address
            if (candidate != network.address) yield(candidate)
        }
    }

    companion object {
        val CCTV_PORTS = listOf(80, 554, 8000, 8080, 8554, 8899, 5000, 37777)
        private val CCTV_SIGNATURE_PORTS = setOf(554, 8000, 8554, 8899, 5000, 37777)
        private const val CONNECT_TIMEOUT_MS = 250
        private const val MAX_CONCURRENT_HOSTS = 32
        const val MAX_HOSTS = 1_024
        private const val MIN_SUPPORTED_PREFIX = 16
        private const val IPV4_MASK = 0xffff_ffffL
    }
}
