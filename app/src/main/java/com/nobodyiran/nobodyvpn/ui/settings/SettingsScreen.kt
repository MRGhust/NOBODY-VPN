package com.nobodyiran.nobodyvpn.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.nobodyiran.nobodyvpn.BuildConfig
import com.nobodyiran.nobodyvpn.R
import com.nobodyiran.nobodyvpn.core.XrayCore
import com.nobodyiran.nobodyvpn.data.Repo
import com.nobodyiran.nobodyvpn.data.RoutingPreset
import com.nobodyiran.nobodyvpn.ui.UiBus
import com.nobodyiran.nobodyvpn.ui.components.SwitchSetting
import com.nobodyiran.nobodyvpn.ui.components.TextSetting
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenPerApp: () -> Unit, onOpenLogs: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }
    val settings by repo.settings.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.nav_settings)) })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionCard(title = stringResource(R.string.settings_general)) {
                MenuSetting(
                    title = stringResource(R.string.theme_mode),
                    options = listOf(
                        stringResource(R.string.theme_system) to 0,
                        stringResource(R.string.theme_light) to 1,
                        stringResource(R.string.theme_dark) to 2
                    ),
                    selected = settings.themeMode,
                    display = {
                        when (it) {
                            1 -> stringResource(R.string.theme_light)
                            2 -> stringResource(R.string.theme_dark)
                            else -> stringResource(R.string.theme_system)
                        }
                    }
                ) { v -> scope.launch { repo.setThemeMode(v) } }

                SwitchSetting(
                    title = stringResource(R.string.dynamic_colors),
                    summary = stringResource(R.string.dynamic_colors_summary),
                    checked = settings.dynamicColors
                ) { v -> scope.launch { repo.setDynamicColors(v) } }

                MenuSetting(
                    title = stringResource(R.string.language),
                    options = listOf(
                        stringResource(R.string.lang_system) to "",
                        stringResource(R.string.lang_fa) to "fa",
                        stringResource(R.string.lang_en) to "en"
                    ),
                    // AppCompatDelegate is the single source of truth for the in-app
                    // language (it persists locales across process death via
                    // autoStoreLocales). Reading it here keeps the UI consistent
                    // and avoids the DataStore race that previously bounced the
                    // app back to English.
                    selected = when (AppCompatDelegate.getApplicationLocales().toLanguageTags()) {
                        "fa" -> "fa"
                        "en" -> "en"
                        else -> ""
                    },
                    display = {
                        when (it) {
                            "fa" -> stringResource(R.string.lang_fa)
                            "en" -> stringResource(R.string.lang_en)
                            else -> stringResource(R.string.lang_system)
                        }
                    }
                ) { v ->
                    // Apply instantly (triggers activity recreation with the new locale)
                    // and mirror the choice into DataStore for informational purposes.
                    val locales = when (v) {
                        "fa" -> LocaleListCompat.forLanguageTags("fa")
                        "en" -> LocaleListCompat.forLanguageTags("en")
                        else -> LocaleListCompat.getEmptyLocaleList()
                    }
                    AppCompatDelegate.setApplicationLocales(locales)
                    scope.launch { repo.setLanguage(v) }
                }

                SwitchSetting(
                    title = stringResource(R.string.auto_connect_boot),
                    checked = settings.autoConnectBoot
                ) { v -> scope.launch { repo.setAutoConnectBoot(v) } }
            }

            SectionCard(title = stringResource(R.string.settings_vpn)) {
                TextSetting(
                    title = stringResource(R.string.mtu),
                    value = settings.mtu.toString(),
                    onCommit = { v -> scope.launch { repo.setMtu(v.toIntOrNull()?.coerceIn(1280, 16000) ?: 1500) } }
                )
                TextSetting(
                    title = stringResource(R.string.vpn_dns_title),
                    summary = stringResource(R.string.vpn_dns_summary),
                    value = settings.vpnDns,
                    onCommit = { v -> scope.launch { repo.setVpnDns(v) } }
                )
                SwitchSetting(
                    title = stringResource(R.string.bypass_lan),
                    checked = settings.bypassLan
                ) { v -> scope.launch { repo.setBypassLan(v) } }
                SwitchSetting(
                    title = stringResource(R.string.ipv6_enabled),
                    checked = settings.ipv6Enabled
                ) { v -> scope.launch { repo.setIpv6Enabled(v) } }
                RowLink(
                    title = stringResource(R.string.per_app_proxy),
                    summary = stringResource(R.string.per_app_summary),
                    onClick = onOpenPerApp
                )
            }

            SectionCard(title = stringResource(R.string.settings_dns)) {
                SwitchSetting(
                    title = stringResource(R.string.dns_enable_custom),
                    summary = stringResource(R.string.dns_enable_custom_summary),
                    checked = settings.customDnsEnabled
                ) { v -> scope.launch { repo.setCustomDnsEnabled(v) } }
                MenuSetting(
                    title = stringResource(R.string.remote_dns),
                    options = listOf(
                        "Google DoH" to "https://8.8.8.8/dns-query",
                        "Cloudflare DoH" to "https://1.1.1.1/dns-query",
                        "Cloudflare UDP" to "1.1.1.1",
                        "Quad9 DoH" to "https://9.9.9.9/dns-query",
                        "AdGuard DoH" to "https://dns.adguard-dns.com/dns-query",
                        "Shecan" to "178.22.122.100"
                    ),
                    selected = settings.remoteDns,
                    display = { it }
                ) { v -> scope.launch { repo.setRemoteDns(v) } }
                MenuSetting(
                    title = stringResource(R.string.dns_query_strategy),
                    options = listOf(
                        "UseIP" to "UseIP",
                        "UseIPv4" to "UseIPv4",
                        "UseIPv6" to "UseIPv6",
                        "UseIPv4v6" to "UseIPv4v6"
                    ),
                    selected = settings.dnsQueryStrategy,
                    display = { it }
                ) { v -> scope.launch { repo.setDnsQueryStrategy(v) } }
            }

            SectionCard(title = stringResource(R.string.settings_routing)) {
                MenuSetting(
                    title = stringResource(R.string.routing_preset),
                    options = listOf(
                        stringResource(R.string.preset_proxy_all) to RoutingPreset.PROXY_ALL,
                        stringResource(R.string.preset_bypass_ir) to RoutingPreset.BYPASS_IR,
                        stringResource(R.string.preset_custom) to RoutingPreset.CUSTOM
                    ),
                    selected = settings.routingPreset,
                    display = {
                        when (it) {
                            RoutingPreset.PROXY_ALL -> stringResource(R.string.preset_proxy_all)
                            RoutingPreset.CUSTOM -> stringResource(R.string.preset_custom)
                            else -> stringResource(R.string.preset_bypass_ir)
                        }
                    }
                ) { v -> scope.launch { repo.setRoutingPreset(v) } }
                MenuSetting(
                    title = stringResource(R.string.routing_domain_strategy),
                    options = listOf(
                        "AsIs" to "AsIs",
                        "IPIfNonMatch" to "IPIfNonMatch",
                        "IPOnDemand" to "IPOnDemand"
                    ),
                    selected = settings.routingDomainStrategy,
                    display = { it }
                ) { v -> scope.launch { repo.setDomainStrategy(v) } }
                SwitchSetting(
                    title = stringResource(R.string.block_ads),
                    checked = settings.blockAds
                ) { v -> scope.launch { repo.setBlockAds(v) } }
                SwitchSetting(
                    title = stringResource(R.string.block_udp),
                    checked = settings.blockUdp
                ) { v -> scope.launch { repo.setBlockUdp(v) } }
                CustomRulesRow(
                    direct = settings.customDirectDomains,
                    proxy = settings.customProxyDomains,
                    block = settings.customBlockDomains
                ) { d, p, b -> scope.launch { repo.setCustomRules(d, p, b) } }
            }

            SectionCard(title = stringResource(R.string.settings_subscription)) {
                SwitchSetting(
                    title = stringResource(R.string.sub_auto_update),
                    checked = settings.subAutoUpdate
                ) { v -> scope.launch { repo.setSubAutoUpdate(v) } }
                TextSetting(
                    title = stringResource(R.string.sub_interval),
                    value = settings.subIntervalMinutes.toString(),
                    onCommit = { v -> scope.launch { repo.setSubInterval(v.toIntOrNull()?.coerceIn(5, 1440) ?: 60) } }
                )
                SwitchSetting(
                    title = stringResource(R.string.sub_on_start),
                    checked = settings.subUpdateOnStart
                ) { v -> scope.launch { repo.setSubUpdateOnStart(v) } }
                TextSetting(
                    title = stringResource(R.string.sub_user_agent),
                    value = settings.subUserAgent,
                    onCommit = { v -> scope.launch { repo.setSubUserAgent(v) } }
                )
            }

            SectionCard(title = stringResource(R.string.settings_logs)) {
                MenuSetting(
                    title = stringResource(R.string.log_level),
                    options = listOf("debug" to "debug", "info" to "info", "warning" to "warning", "error" to "error"),
                    selected = settings.logLevel,
                    display = { it }
                ) { v -> scope.launch { repo.setLogLevel(v) } }
                RowLink(
                    title = stringResource(R.string.log_title),
                    summary = null,
                    onClick = onOpenLogs
                )
            }

            SectionCard(title = stringResource(R.string.settings_about)) {
                InfoRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
                InfoRow(stringResource(R.string.about_core_version), XrayCore.version())
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.footer_credit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            content()
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun <T> MenuSetting(
    title: String,
    options: List<Pair<String, T>>,
    selected: T?,
    display: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        TextButton(onClick = { open = true }) {
            Text(if (selected != null) display(selected) else "", color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (label, value) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

@Composable
private fun RowLink(title: String, summary: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = onClick) { Text("…") }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CustomRulesRow(
    direct: List<String>,
    proxy: List<String>,
    block: List<String>,
    onCommit: (List<String>, List<String>, List<String>) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var dText by remember(direct) { mutableStateOf(direct.joinToString("\n")) }
    var pText by remember(proxy) { mutableStateOf(proxy.joinToString("\n")) }
    var bText by remember(block) { mutableStateOf(block.joinToString("\n")) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.custom_rules), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.custom_rules_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = { open = true }) { Text("…") }
    }

    if (open) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.custom_rules)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.rules_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = dText,
                        onValueChange = { dText = it },
                        label = { Text(stringResource(R.string.rule_direct_domains)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = pText,
                        onValueChange = { pText = it },
                        label = { Text(stringResource(R.string.rule_proxy_domains)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = bText,
                        onValueChange = { bText = it },
                        label = { Text(stringResource(R.string.rule_block_domains)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onCommit(
                        dText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                        pText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                        bText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    )
                    open = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
