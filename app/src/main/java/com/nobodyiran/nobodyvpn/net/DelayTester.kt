package com.nobodyiran.nobodyvpn.net

import com.nobodyiran.nobodyvpn.core.ConfigBuilder
import com.nobodyiran.nobodyvpn.core.XrayCore
import com.nobodyiran.nobodyvpn.data.Profile
import com.nobodyiran.nobodyvpn.data.Settings
import com.nobodyiran.nobodyvpn.util.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Delay testing: fast TCP handshake ping and real ping through a temporary core instance.
 */
object DelayTester {

    /** TCP handshake delay in ms, or -1 on failure. */
    suspend fun tcpPing(host: String, port: Int, timeoutMs: Int = 5000): Long =
        withContext(Dispatchers.IO) {
            if (host.isBlank() || port <= 0) return@withContext -1L
            try {
                Socket().use { socket ->
                    val t0 = System.nanoTime()
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    (System.nanoTime() - t0) / 1_000_000
                }
            } catch (_: Exception) {
                -1L
            }
        }

    /** Real HTTP delay through a temporary core instance, or -1 on failure. */
    suspend fun realPing(profile: Profile, settings: Settings): Long =
        withContext(Dispatchers.IO) {
            try {
                val config = ConfigBuilder.build(profile, settings, vpnMode = false)
                val url = settings.testUrl.ifBlank { C.DELAY_TEST_URL }
                val d = XrayCore.measureOutbound(config, url)
                if (d > 0) d else -1L
            } catch (_: Exception) {
                -1L
            }
        }

    /**
     * Tests many profiles concurrently.
     * @param mode true = real ping, false = TCP ping
     * @param onResult called per finished profile
     * @return map of profileId -> delay (-1 = failed)
     */
    suspend fun testAll(
        profiles: List<Profile>,
        settings: Settings,
        real: Boolean,
        concurrency: Int = 4,
        onResult: suspend (id: String, delay: Long) -> Unit
    ): Map<String, Long> = coroutineScope {
        val results = java.util.concurrent.ConcurrentHashMap<String, Long>()
        val semaphore = Semaphore(concurrency.coerceIn(1, 16))
        val jobs = profiles.map { profile ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val delay = if (real) {
                        realPing(profile, settings)
                    } else {
                        tcpPing(profile.address, profile.port)
                    }
                    results[profile.id] = delay
                    onResult(profile.id, delay)
                }
            }
        }
        jobs.awaitAll()
        results.toMap()
    }
}
