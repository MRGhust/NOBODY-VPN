package com.nobodyiran.nobodyvpn.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.nobodyiran.nobodyvpn.core.ConfigBuilder
import com.nobodyiran.nobodyvpn.core.XrayCore
import com.nobodyiran.nobodyvpn.data.Repo
import com.nobodyiran.nobodyvpn.data.Settings
import com.nobodyiran.nobodyvpn.util.C
import com.nobodyiran.nobodyvpn.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.net.Network
import java.util.concurrent.atomic.AtomicBoolean

class NobodyVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.nobodyiran.nobodyvpn.action.CONNECT"
        const val ACTION_DISCONNECT = "com.nobodyiran.nobodyvpn.action.DISCONNECT"
        const val ACTION_RECONNECT = "com.nobodyiran.nobodyvpn.action.RECONNECT"

        fun start(context: Context, action: String) {
            val intent = Intent(context, NobodyVpnService::class.java).setAction(action)
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val starting = AtomicBoolean(false)
    private var tun: ParcelFileDescriptor? = null
    private var timerJob: Job? = null
    private var statsJob: Job? = null
    private var connectStartMs = 0L

    override fun onCreate() {
        super.onCreate()
        XrayCore.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_CONNECT

        when (action) {
            ACTION_DISCONNECT -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_RECONNECT -> {
                stopEverything()
                scope.launch {
                    delay(500)
                    connect()
                }
                return START_STICKY
            }
            else -> {
                scope.launch { connect() }
                return START_STICKY
            }
        }
    }

    private fun connect() {
        if (!starting.compareAndSet(false, true)) return
        try {
            VpnState.setState(ConnState.CONNECTING)
            // Foreground first
            val notification = NotificationHelper.build(this, false, "", null)
            ServiceCompat.startForeground(
                this,
                NotificationHelper.FOREGROUND_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )

            val repo = Repo.get(applicationContext)
            val settings = repo.settings.value
            val selectedId = repo.selectedId.value
            val profile = repo.profiles.value.firstOrNull { it.id == selectedId }
            if (profile == null) {
                VpnState.setError(applicationContext.getString(com.nobodyiran.nobodyvpn.R.string.err_no_config))
                stopEverything(resetStateToError = false)
                return
            }

            val config = ConfigBuilder.build(profile, settings, vpnMode = true)
            XrayCore.appendLog("=== connecting to: ${profile.remark} ===")

            val fd = establishVpn(settings, profile.remark)
            if (fd == -1) {
                VpnState.setError(applicationContext.getString(com.nobodyiran.nobodyvpn.R.string.err_no_permission))
                stopEverything(resetStateToError = false)
                return
            }

            val ok = XrayCore.start(config, fd)
            if (!ok) {
                VpnState.setError("core start failed")
                stopEverything(resetStateToError = false)
                return
            }

            connectStartMs = SystemClock.elapsedRealtime()
            VpnState.setRunning(profile.remark)
            VpnState.resetSession()
            VpnState.setState(ConnState.CONNECTED)
            startTimers(settings)
            updateNotification()
        } catch (e: Exception) {
            VpnState.setError(e.message ?: e.javaClass.simpleName)
            stopEverything(resetStateToError = false)
        } finally {
            starting.set(false)
        }
    }

    private fun establishVpn(settings: Settings, remark: String): Int {
        val prepare = prepare(this)
        if (prepare != null) return -1

        val builder = Builder()
            .setSession(remark.ifBlank { "NobodyVPN" })
            .setMtu(settings.mtu)
            .addAddress(C.VPN_IPV4_CLIENT, 30)

        if (settings.bypassLan) {
            C.ROUTED_IP_LIST.forEach {
                val parts = it.split('/')
                builder.addRoute(parts[0], parts[1].toInt())
            }
        } else {
            builder.addRoute("0.0.0.0", 0)
        }

        if (settings.ipv6Enabled) {
            builder.addAddress(C.VPN_IPV6_CLIENT, 126)
            if (settings.bypassLan) {
                builder.addRoute("2000::", 3)
                builder.addRoute("fc00::", 18)
            } else {
                builder.addRoute("::", 0)
            }
        }

        settings.vpnDns.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { dns ->
            if (dns.matches(Regex("^[0-9.]+$"))) {
                try {
                    builder.addDnsServer(dns)
                } catch (_: Exception) {
                }
            }
        }

        // Per-app proxy (split tunneling)
        val self = packageName
        if (settings.perAppEnabled && settings.perAppSet.isNotEmpty()) {
            if (settings.perAppExcludeMode) {
                val apps = settings.perAppSet + self
                apps.forEach { pkg ->
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (_: PackageManager.NameNotFoundException) {
                    }
                }
            } else {
                settings.perAppSet.forEach { pkg ->
                    try {
                        builder.addAllowedApplication(pkg)
                    } catch (_: PackageManager.NameNotFoundException) {
                    }
                }
            }
        } else {
            try {
                builder.addDisallowedApplication(self)
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }

        try {
            builder.setMetered(false)
        } catch (_: Exception) {
        }

        val pfd = builder.establish() ?: return -1
        tun?.close()
        tun = pfd
        return pfd.fd
    }

    private fun startTimers(settings: Settings) {
        stopTimers()
        timerJob = scope.launch {
            while (true) {
                VpnState.setElapsed((SystemClock.elapsedRealtime() - connectStartMs) / 1000)
                delay(1000)
            }
        }
        if (settings.speedEnabled) {
            statsJob = scope.launch {
                while (true) {
                    pollStats()
                    delay(1000)
                }
            }
        }
    }

    private fun stopTimers() {
        timerJob?.cancel(); timerJob = null
        statsJob?.cancel(); statsJob = null
    }

    private var lastUp = 0L
    private var lastDown = 0L
    private var pendingUp = 0L
    private var pendingDown = 0L

    private suspend fun pollStats() {
        val stats = XrayCore.queryStats()
        val proxy = stats[C.TAG_PROXY] ?: (0L to 0L)
        val up = proxy.first
        val down = proxy.second
        val deltaUp = (up - lastUp).coerceAtLeast(0)
        val deltaDown = (down - lastDown).coerceAtLeast(0)
        lastUp = up
        lastDown = down

        VpnState.addSession(deltaUp, deltaDown)
        VpnState.setSpeed(deltaUp, deltaDown)
        pendingUp += deltaUp
        pendingDown += deltaDown

        // Update notification every second (cheap) and persist totals every 10s
        updateNotification()
        if (pendingUp + pendingDown > 0) {
            if (SystemClock.elapsedRealtime() - lastPersist > 10_000) {
                persistTotals()
            }
        }
    }

    private var lastPersist = 0L

    private fun persistTotals() {
        lastPersist = SystemClock.elapsedRealtime()
        val up = pendingUp; val down = pendingDown
        pendingUp = 0; pendingDown = 0
        if (up + down > 0) {
            scope.launch {
                runCatching { Repo.get(applicationContext).addTotals(up, down) }
            }
        }
    }

    private fun updateNotification() {
        if (VpnState.state.value != ConnState.CONNECTED) return
        val up = VpnState.upSpeed.value
        val down = VpnState.downSpeed.value
        val speedLine = "↓ ${Fmt.speed(down)}   ↑ ${Fmt.speed(up)}"
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(
                NotificationHelper.FOREGROUND_ID,
                NotificationHelper.build(this, true, VpnState.runningRemark.value, speedLine)
            )
        } catch (_: Exception) {
        }
    }

    private fun stopEverything(resetStateToError: Boolean = true) {
        stopTimers()
        persistTotals()
        XrayCore.stop()
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        scope.launch {
            delay(150)
            try {
                tun?.close()
            } catch (_: Exception) {
            }
            tun = null
        }
        if (resetStateToError) {
            VpnState.setState(ConnState.DISCONNECTED)
        } else {
            if (VpnState.state.value != ConnState.ERROR) {
                VpnState.setState(ConnState.DISCONNECTED)
            }
        }
        stopSelf()
    }

    override fun onRevoke() {
        // Permission revoked or Always-On switched by the system
        VpnState.setError(null)
        stopEverything()
    }

    override fun onDestroy() {
        stopTimers()
        try {
            tun?.close()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun setUnderlyingNetworks(networks: Array<Network>?): Boolean {
        return try {
            super.setUnderlyingNetworks(networks)
        } catch (_: Exception) {
            false
        }
    }
}
