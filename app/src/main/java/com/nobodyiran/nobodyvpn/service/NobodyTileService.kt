package com.nobodyiran.nobodyvpn.service

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nobodyiran.nobodyvpn.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NobodyTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
        collectJob?.cancel()
        collectJob = scope.launch {
            VpnState.state.collect { updateTile() }
        }
    }

    override fun onStopListening() {
        collectJob?.cancel()
        collectJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (VpnState.state.value) {
            ConnState.CONNECTED, ConnState.CONNECTING, ConnState.DISCONNECTING -> {
                NobodyVpnService.start(applicationContext, NobodyVpnService.ACTION_DISCONNECT)
            }
            else -> {
                // The VPN consent dialog can't be shown from a tile — open the app instead.
                val consent = try { VpnService.prepare(applicationContext) } catch (_: Exception) { null }
                if (consent != null) {
                    openAppForConnect()
                    updateTile()
                    return
                }
                NobodyVpnService.start(applicationContext, NobodyVpnService.ACTION_CONNECT)
            }
        }
        updateTile()
    }

    private fun openAppForConnect() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_REQUEST_CONNECT, true)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (_: Exception) {
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val state = VpnState.state.value
        try {
            when (state) {
                ConnState.CONNECTED -> {
                    tile.state = Tile.STATE_ACTIVE
                    tile.subtitle = VpnState.runningRemark.value.ifBlank { "NOBODY VPN" }
                }
                ConnState.CONNECTING, ConnState.DISCONNECTING -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.subtitle = "…"
                }
                else -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.subtitle = "NOBODY VPN"
                }
            }
            tile.updateTile()
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
