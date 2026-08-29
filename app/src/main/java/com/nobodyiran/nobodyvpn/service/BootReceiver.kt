package com.nobodyiran.nobodyvpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nobodyiran.nobodyvpn.data.Repo

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        try {
            val repo = Repo.get(context)
            val settings = repo.settings.value
            if (!settings.autoConnectBoot) return
            if (repo.selectedId.value.isNullOrBlank()) return
            NobodyVpnService.start(context, NobodyVpnService.ACTION_CONNECT)
        } catch (_: Exception) {
        }
    }
}
