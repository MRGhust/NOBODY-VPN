package com.nobodyiran.nobodyvpn

import android.app.Application
import com.nobodyiran.nobodyvpn.core.XrayCore
import com.nobodyiran.nobodyvpn.data.Repo
import com.nobodyiran.nobodyvpn.net.SubUpdater
import com.nobodyiran.nobodyvpn.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        val repo = Repo.get(this)
        appScope.launch {
            XrayCore.init(this@App)
        }
        appScope.launch {
            val settings = repo.settings.value
            if (settings.subUpdateOnStart) {
                delay(2500)
                updateAllSubscriptions(repo)
            }
        }
        appScope.launch {
            // Periodic subscription auto-update
            while (true) {
                val s = repo.settings.value
                if (s.subAutoUpdate) {
                    val interval = s.subIntervalMinutes.coerceIn(5, 1440)
                    val subs = repo.subs.value.filter { it.enabled }
                    val now = System.currentTimeMillis()
                    subs.forEach { sub ->
                        if (now - sub.lastUpdate > interval * 60_000L) {
                            val res = SubUpdater.fetch(sub.url, s.subUserAgent)
                            if (res.ok && res.content != null) {
                                val profiles = SubUpdater.toProfiles(res.content, sub.id)
                                repo.addProfiles(profiles, replaceSubscriptionId = sub.id)
                                repo.saveSubscription(sub.copy(lastUpdate = System.currentTimeMillis()))
                            }
                        }
                    }
                    delay(interval * 60_000L)
                } else {
                    delay(5 * 60_000L)
                }
            }
        }
    }

    private suspend fun updateAllSubscriptions(repo: Repo) {
        val s = repo.settings.value
        repo.subs.value.filter { it.enabled }.forEach { sub ->
            val res = SubUpdater.fetch(sub.url, s.subUserAgent)
            if (res.ok && res.content != null) {
                val profiles = SubUpdater.toProfiles(res.content, sub.id)
                if (profiles.isNotEmpty()) {
                    repo.addProfiles(profiles, replaceSubscriptionId = sub.id)
                    repo.saveSubscription(sub.copy(lastUpdate = System.currentTimeMillis()))
                }
            }
        }
    }
}
