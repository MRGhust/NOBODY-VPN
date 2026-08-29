package com.nobodyiran.nobodyvpn.core

import android.content.Context
import com.nobodyiran.nobodyvpn.util.C
import go.Seq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

/**
 * Thin singleton wrapper around the libv2ray (Xray core) gomobile bindings.
 */
object XrayCore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    @Volatile
    private var initialized = false

    @Volatile
    private var controller: CoreController? = null

    /** Copy geo assets to filesDir and init the core environment. Must be called before start(). */
    fun init(appContext: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                val filesDir: File = appContext.filesDir
                listOf("geoip.dat", "geosite.dat").forEach { name ->
                    val target = File(filesDir, name)
                    if (!target.exists() || target.length() == 0L) {
                        appContext.assets.open(name).use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
                Seq.setContext(appContext.applicationContext)
                Libv2ray.initCoreEnv(filesDir.absolutePath, xudpBaseKey(appContext))
                appendLog("Xray core environment initialized")
                initialized = true
            } catch (e: Exception) {
                appendLog("init error: ${e.message}")
            }
        }
    }

    /**
     * Xray core requires the XUDP base key to be exactly 32 bytes, base64url-encoded
     * (see common/xudp in Xray-core: "BaseKey must be 32 bytes"). Passing the raw
     * package name crashes core init with:
     * "core init failed: ... xray.xudp.basekey: invalid value".
     * We derive a stable 32-byte key from ANDROID_ID, exactly like v2rayNG does.
     */
    private fun xudpBaseKey(context: Context): String = try {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ) ?: "NobodyVPN-XUDP-BaseKey"
        val raw = androidId.toByteArray(Charsets.UTF_8).copyOf(32) // pad/truncate to 32 bytes
        android.util.Base64.encodeToString(
            raw,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    } catch (_: Exception) {
        "" // empty -> the wrapper skips the env var and Xray generates a random key
    }

    private fun ensureController(): CoreController {
        controller?.let { return it }
        return Libv2ray.newCoreController(Callback()).also {
            controller = it
        }
    }

    private class Callback : CoreCallbackHandler {
        override fun startup(): Long {
            XrayCore.appendLog("core startup")
            return 0
        }

        override fun shutdown(): Long {
            XrayCore.appendLog("core shutdown")
            XrayCore._isRunning.value = false
            return 0
        }

        override fun onEmitStatus(l: Long, s: String?): Long {
            if (!s.isNullOrBlank()) XrayCore.appendLog(s)
            return 0
        }
    }

    /**
     * Starts the core with the given config. [tunFd] = VPN tun descriptor, 0 for proxy-only.
     * Blocking; call from a background thread.
     */
    fun start(configJson: String, tunFd: Int): Boolean {
        return kotlinx.coroutines.runBlocking {
            startMutex.withLock {
                try {
                    val c = ensureController()
                    c.startLoop(configJson, tunFd)
                    val running = try { c.isRunning } catch (_: Exception) { false }
                    _isRunning.value = running
                    appendLog("core start -> running=$running")
                    running
                } catch (e: Exception) {
                    _isRunning.value = false
                    appendLog("start error: ${e.message ?: e.javaClass.simpleName}")
                    false
                }
            }
        }
    }

    fun stop() {
        scope.launch {
            try {
                startMutex.withLock {
                    controller?.let { c ->
                        if (try { c.isRunning } catch (_: Exception) { false }) {
                            c.stopLoop()
                        }
                    }
                }
            } catch (e: Exception) {
                appendLog("stop error: ${e.message}")
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun version(): String = try {
        Libv2ray.checkVersionX()
    } catch (_: Exception) {
        "unknown"
    }

    /**
     * Real delay test through a temporary instance built from [configJson].
     * The Go side strips inbounds, so no port conflicts occur. Call on IO.
     */
    fun measureOutbound(configJson: String, url: String): Long = try {
        Libv2ray.measureOutboundDelay(configJson, url)
    } catch (e: Exception) {
        -1L
    }

    /** Queries and resets traffic counters. Returns map tag->(up,down). */
    fun queryStats(): Map<String, Pair<Long, Long>> {
        val c = controller ?: return emptyMap()
        return try {
            val payload = c.queryAllOutboundTrafficStats() ?: return emptyMap()
            val result = mutableMapOf<String, Pair<Long, Long>>()
            payload.split(';').forEach { entry ->
                if (entry.isBlank()) return@forEach
                val parts = entry.split(',', limit = 3)
                if (parts.size != 3) return@forEach
                val tag = parts[0]
                val direction = parts[1]
                val value = parts[2].toLongOrNull() ?: return@forEach
                val (up, down) = result[tag] ?: (0L to 0L)
                result[tag] = if (direction == "uplink") (up + value) to down else up to (down + value)
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun appendLog(line: String) {
        synchronized(_logs) {
            val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())
            val list = _logs.value.toMutableList()
            list.add("[$ts] $line")
            while (list.size > 400) list.removeAt(0)
            _logs.value = list
        }
    }

    fun clearLogs() {
        synchronized(_logs) { _logs.value = emptyList() }
    }
}
