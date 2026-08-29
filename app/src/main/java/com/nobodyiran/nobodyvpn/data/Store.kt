package com.nobodyiran.nobodyvpn.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "nobody_vpn")

class Repo private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _subs = MutableStateFlow<List<Subscription>>(emptyList())
    val subs: StateFlow<List<Subscription>> = _subs.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _totalUp = MutableStateFlow(0L)
    val totalUp: StateFlow<Long> = _totalUp.asStateFlow()

    private val _totalDown = MutableStateFlow(0L)
    val totalDown: StateFlow<Long> = _totalDown.asStateFlow()

    init {
        scope.launch {
            appContext.dataStore.data.collect { prefs ->
                try {
                    _profiles.value = prefs[K_PROFILES]?.let { runCatching { json.decodeFromString<List<Profile>>(it) }.getOrNull() } ?: emptyList()
                    _subs.value = prefs[K_SUBS]?.let { runCatching { json.decodeFromString<List<Subscription>>(it) }.getOrNull() } ?: emptyList()
                    _selectedId.value = prefs[K_SELECTED]?.takeIf { it.isNotBlank() }
                    _totalUp.value = prefs[K_TOTAL_UP] ?: 0L
                    _totalDown.value = prefs[K_TOTAL_DOWN] ?: 0L
                    _settings.value = prefs.toSettings()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun Preferences.toSettings(): Settings {
        val direct = this[K_RULE_DIRECT] ?: ""
        val proxy = this[K_RULE_PROXY] ?: ""
        val block = this[K_RULE_BLOCK] ?: ""
        return Settings(
            themeMode = this[K_THEME] ?: 0,
            dynamicColors = this[K_DYNAMIC] ?: false,
            language = this[K_LANG] ?: "",
            autoConnectBoot = this[K_BOOT] ?: false,
            mtu = this[K_MTU] ?: 1500,
            vpnDns = this[K_VPN_DNS] ?: "1.1.1.1",
            bypassLan = this[K_BYPASS_LAN] ?: false,
            ipv6Enabled = this[K_IPV6] ?: false,
            perAppEnabled = this[K_PER_APP] ?: false,
            perAppSet = this[K_PER_APP_SET] ?: emptySet(),
            perAppExcludeMode = this[K_PER_APP_MODE] ?: true,
            customDnsEnabled = this[K_CUSTOM_DNS] ?: true,
            remoteDns = this[K_REMOTE_DNS] ?: "https://8.8.8.8/dns-query",
            dnsQueryStrategy = this[K_DNS_STRATEGY] ?: "UseIP",
            routingPreset = this[K_ROUTING_PRESET] ?: RoutingPreset.BYPASS_IR,
            routingDomainStrategy = this[K_DOMAIN_STRATEGY] ?: "IPIfNonMatch",
            blockAds = this[K_BLOCK_ADS] ?: false,
            blockUdp = this[K_BLOCK_UDP] ?: false,
            customDirectDomains = direct.lines().map { it.trim() }.filter { it.isNotEmpty() },
            customProxyDomains = proxy.lines().map { it.trim() }.filter { it.isNotEmpty() },
            customBlockDomains = block.lines().map { it.trim() }.filter { it.isNotEmpty() },
            subAutoUpdate = this[K_SUB_AUTO] ?: true,
            subIntervalMinutes = this[K_SUB_INTERVAL] ?: 60,
            subUpdateOnStart = this[K_SUB_ONSTART] ?: false,
            subUserAgent = this[K_SUB_UA] ?: "NobodyVPN/1.0 (Android)",
            logLevel = this[K_LOGLEVEL] ?: "warning",
            speedEnabled = this[K_SPEED_ENABLED] ?: true,
            testUrl = this[K_TEST_URL] ?: "https://www.gstatic.com/generate_204"
        )
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        appContext.dataStore.edit(block)
    }

    // ---------- Profiles ----------

    suspend fun addProfiles(newOnes: List<Profile>, replaceSubscriptionId: String? = null): Int {
        if (newOnes.isEmpty()) return 0
        edit { p ->
            val current = p[K_PROFILES]?.let { runCatching { json.decodeFromString<List<Profile>>(it) }.getOrNull() } ?: emptyList()
            val filtered = if (replaceSubscriptionId != null) current.filterNot { it.subscriptionId == replaceSubscriptionId } else current
            val merged = filtered + newOnes
            p[K_PROFILES] = json.encodeToString(merged)
        }
        return newOnes.size
    }

    suspend fun deleteProfile(id: String) {
        edit { p ->
            val current = p[K_PROFILES]?.let { runCatching { json.decodeFromString<List<Profile>>(it) }.getOrNull() } ?: emptyList()
            p[K_PROFILES] = json.encodeToString(current.filterNot { it.id == id })
            if (p[K_SELECTED] == id) p[K_SELECTED] = ""
        }
    }

    suspend fun updateProfile(profile: Profile) {
        edit { p ->
            val current = p[K_PROFILES]?.let { runCatching { json.decodeFromString<List<Profile>>(it) }.getOrNull() } ?: emptyList()
            p[K_PROFILES] = json.encodeToString(current.map { if (it.id == profile.id) profile else it })
        }
    }

    suspend fun setDelays(results: Map<String, Long>) {
        if (results.isEmpty()) return
        edit { p ->
            val current = p[K_PROFILES]?.let { runCatching { json.decodeFromString<List<Profile>>(it) }.getOrNull() } ?: emptyList()
            val now = System.currentTimeMillis()
            p[K_PROFILES] = json.encodeToString(current.map {
                if (results.containsKey(it.id)) it.copy(delayMs = results[it.id] ?: -1L, lastTested = now) else it
            })
        }
    }

    suspend fun select(id: String?) {
        edit { it[K_SELECTED] = id ?: "" }
    }

    // ---------- Subscriptions ----------

    suspend fun saveSubscription(sub: Subscription) {
        edit { p ->
            val current = p[K_SUBS]?.let { runCatching { json.decodeFromString<List<Subscription>>(it) }.getOrNull() } ?: emptyList()
            val exists = current.any { it.id == sub.id }
            p[K_SUBS] = json.encodeToString(if (exists) current.map { if (it.id == sub.id) sub else it } else current + sub)
        }
    }

    suspend fun deleteSubscription(id: String) {
        edit { p ->
            val current = p[K_SUBS]?.let { runCatching { json.decodeFromString<List<Subscription>>(it) }.getOrNull() } ?: emptyList()
            p[K_SUBS] = json.encodeToString(current.filterNot { it.id == id })
            val profiles = p[K_PROFILES]?.let { runCatching { json.decodeFromString<List<Profile>>(it) }.getOrNull() } ?: emptyList()
            p[K_PROFILES] = json.encodeToString(profiles.filterNot { it.subscriptionId == id })
        }
    }

    suspend fun touchSubscription(id: String, count: Int) {
        // timestamp handled by caller saving sub; kept for future use
    }

    // ---------- Settings ----------

    suspend fun setThemeMode(v: Int) = edit { it[K_THEME] = v }
    suspend fun setDynamicColors(v: Boolean) = edit { it[K_DYNAMIC] = v }
    suspend fun setLanguage(v: String) = edit { it[K_LANG] = v }
    suspend fun setAutoConnectBoot(v: Boolean) = edit { it[K_BOOT] = v }
    suspend fun setMtu(v: Int) = edit { it[K_MTU] = v }
    suspend fun setVpnDns(v: String) = edit { it[K_VPN_DNS] = v }
    suspend fun setBypassLan(v: Boolean) = edit { it[K_BYPASS_LAN] = v }
    suspend fun setIpv6Enabled(v: Boolean) = edit { it[K_IPV6] = v }
    suspend fun setPerAppEnabled(v: Boolean) = edit { it[K_PER_APP] = v }
    suspend fun setPerAppSet(v: Set<String>) = edit { it[K_PER_APP_SET] = v }
    suspend fun setPerAppExcludeMode(v: Boolean) = edit { it[K_PER_APP_MODE] = v }
    suspend fun setCustomDnsEnabled(v: Boolean) = edit { it[K_CUSTOM_DNS] = v }
    suspend fun setRemoteDns(v: String) = edit { it[K_REMOTE_DNS] = v }
    suspend fun setDnsQueryStrategy(v: String) = edit { it[K_DNS_STRATEGY] = v }
    suspend fun setRoutingPreset(v: Int) = edit { it[K_ROUTING_PRESET] = v }
    suspend fun setDomainStrategy(v: String) = edit { it[K_DOMAIN_STRATEGY] = v }
    suspend fun setBlockAds(v: Boolean) = edit { it[K_BLOCK_ADS] = v }
    suspend fun setBlockUdp(v: Boolean) = edit { it[K_BLOCK_UDP] = v }
    suspend fun setCustomRules(direct: List<String>, proxy: List<String>, block: List<String>) = edit {
        it[K_RULE_DIRECT] = direct.joinToString("\n")
        it[K_RULE_PROXY] = proxy.joinToString("\n")
        it[K_RULE_BLOCK] = block.joinToString("\n")
    }
    suspend fun setSubAutoUpdate(v: Boolean) = edit { it[K_SUB_AUTO] = v }
    suspend fun setSubInterval(v: Int) = edit { it[K_SUB_INTERVAL] = v }
    suspend fun setSubUpdateOnStart(v: Boolean) = edit { it[K_SUB_ONSTART] = v }
    suspend fun setSubUserAgent(v: String) = edit { it[K_SUB_UA] = v }
    suspend fun setLogLevel(v: String) = edit { it[K_LOGLEVEL] = v }
    suspend fun setSpeedEnabled(v: Boolean) = edit { it[K_SPEED_ENABLED] = v }

    // ---------- Traffic totals ----------

    suspend fun addTotals(up: Long, down: Long) {
        edit {
            it[K_TOTAL_UP] = (it[K_TOTAL_UP] ?: 0L) + up
            it[K_TOTAL_DOWN] = (it[K_TOTAL_DOWN] ?: 0L) + down
        }
    }

    companion object {
        private val K_PROFILES = stringPreferencesKey("profiles")
        private val K_SUBS = stringPreferencesKey("subs")
        private val K_SELECTED = stringPreferencesKey("selected_id")
        private val K_TOTAL_UP = longPreferencesKey("total_up")
        private val K_TOTAL_DOWN = longPreferencesKey("total_down")

        private val K_THEME = intPreferencesKey("theme_mode")
        private val K_DYNAMIC = booleanPreferencesKey("dynamic_colors")
        private val K_LANG = stringPreferencesKey("language")
        private val K_BOOT = booleanPreferencesKey("auto_connect_boot")
        private val K_MTU = intPreferencesKey("mtu")
        private val K_VPN_DNS = stringPreferencesKey("vpn_dns")
        private val K_BYPASS_LAN = booleanPreferencesKey("bypass_lan")
        private val K_IPV6 = booleanPreferencesKey("ipv6")
        private val K_PER_APP = booleanPreferencesKey("per_app")
        private val K_PER_APP_SET = stringSetPreferencesKey("per_app_set")
        private val K_PER_APP_MODE = booleanPreferencesKey("per_app_mode")
        private val K_CUSTOM_DNS = booleanPreferencesKey("custom_dns")
        private val K_REMOTE_DNS = stringPreferencesKey("remote_dns")
        private val K_DNS_STRATEGY = stringPreferencesKey("dns_strategy")
        private val K_ROUTING_PRESET = intPreferencesKey("routing_preset")
        private val K_DOMAIN_STRATEGY = stringPreferencesKey("domain_strategy")
        private val K_BLOCK_ADS = booleanPreferencesKey("block_ads")
        private val K_BLOCK_UDP = booleanPreferencesKey("block_udp")
        private val K_RULE_DIRECT = stringPreferencesKey("rule_direct")
        private val K_RULE_PROXY = stringPreferencesKey("rule_proxy")
        private val K_RULE_BLOCK = stringPreferencesKey("rule_block")
        private val K_SUB_AUTO = booleanPreferencesKey("sub_auto")
        private val K_SUB_INTERVAL = intPreferencesKey("sub_interval")
        private val K_SUB_ONSTART = booleanPreferencesKey("sub_onstart")
        private val K_SUB_UA = stringPreferencesKey("sub_ua")
        private val K_LOGLEVEL = stringPreferencesKey("loglevel")
        private val K_SPEED_ENABLED = booleanPreferencesKey("speed_enabled")
        private val K_TEST_URL = stringPreferencesKey("test_url")

        @Volatile
        private var instance: Repo? = null

        fun get(context: Context): Repo =
            instance ?: synchronized(this) {
                instance ?: Repo(context.applicationContext).also { instance = it }
            }
    }
}
