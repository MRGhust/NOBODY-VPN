package com.nobodyiran.nobodyvpn.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ProfileType { VLESS, VMESS, TROJAN, SHADOWSOCKS, CUSTOM }

@Serializable
data class Profile(
    val id: String = UUID.randomUUID().toString().replace("-", ""),
    val remark: String = "",
    val type: ProfileType = ProfileType.VLESS,
    val subscriptionId: String? = null,
    /** Generated Xray outbound JSON for share-link based profiles. */
    val outboundJson: String? = null,
    /** Full Xray config JSON for CUSTOM profiles. */
    val fullConfigJson: String? = null,
    val address: String = "",
    val port: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val delayMs: Long = -1L,
    val lastTested: Long = 0L
)

@Serializable
data class Subscription(
    val id: String = UUID.randomUUID().toString().replace("-", ""),
    val remark: String = "",
    val url: String = "",
    val enabled: Boolean = true,
    val lastUpdate: Long = 0L
)

/** Routing presets. */
object RoutingPreset {
    const val PROXY_ALL = 0
    const val BYPASS_IR = 1
    const val CUSTOM = 2
}

data class Settings(
    val themeMode: Int = 0,             // 0 system, 1 light, 2 dark
    val dynamicColors: Boolean = false,
    val language: String = "",          // "" system, "fa", "en"
    val autoConnectBoot: Boolean = false,

    val mtu: Int = 1500,
    val vpnDns: String = "1.1.1.1",
    val bypassLan: Boolean = false,
    val ipv6Enabled: Boolean = false,

    val perAppEnabled: Boolean = false,
    val perAppSet: Set<String> = emptySet(),
    val perAppExcludeMode: Boolean = true, // true = all except selected

    val customDnsEnabled: Boolean = true,
    val remoteDns: String = "https://8.8.8.8/dns-query",
    val dnsQueryStrategy: String = "UseIP",

    val routingPreset: Int = RoutingPreset.BYPASS_IR,
    val routingDomainStrategy: String = "IPIfNonMatch",
    val blockAds: Boolean = false,
    val blockUdp: Boolean = false,
    val customDirectDomains: List<String> = emptyList(),
    val customProxyDomains: List<String> = emptyList(),
    val customBlockDomains: List<String> = emptyList(),

    val subAutoUpdate: Boolean = true,
    val subIntervalMinutes: Int = 60,
    val subUpdateOnStart: Boolean = false,
    val subUserAgent: String = "NobodyVPN/1.0 (Android)",

    val logLevel: String = "warning",
    val speedEnabled: Boolean = true,
    val testUrl: String = "https://www.gstatic.com/generate_204"
)
