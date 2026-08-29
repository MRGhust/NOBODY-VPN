package com.nobodyiran.nobodyvpn.parser

import com.nobodyiran.nobodyvpn.data.Profile
import com.nobodyiran.nobodyvpn.data.ProfileType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Imports a full Xray config JSON as a CUSTOM profile
 * (e.g. subscription-provided JSON like the reference config with inbounds/outbounds/routing).
 */
object JsonConfig {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Protocols that represent a real remote proxy (never used as first outbound fallback). */
    private val PROXY_PROTOCOLS = setOf("vless", "vmess", "trojan", "shadowsocks", "wireguard", "loopback", "hysteria2", "hysteria", "tuic")
    private val NON_PROXY_TAGS = setOf("direct", "block", "blocked", "dns-out", "dns", "api", "freedom", "blackhole", "dns-module", "domestic-dns")

    fun looksLikeConfig(text: String): Boolean {
        val t = text.trim()
        if (!t.startsWith("{")) return false
        return t.contains("\"outbounds\"")
    }

    fun parse(text: String): Profile? {
        return try {
            val obj = json.parseToJsonElement(text).jsonObject
            val remarks = obj["remarks"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: "Custom Config"

            val proxy = findProxyOutbound(obj)
            val address = proxy?.first ?: ""
            val port = proxy?.second ?: 0

            Profile(
                remark = remarks,
                type = ProfileType.CUSTOM,
                fullConfigJson = text.trim(),
                address = address,
                port = port
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Finds (address, port) of the primary proxy outbound for ping tests. */
    private fun findProxyOutbound(obj: JsonObject): Pair<String, Int>? {
        val outbounds = obj["outbounds"]?.jsonArray ?: return null
        for (e in outbounds) {
            val o = (e as? JsonObject) ?: continue
            val protocol = o["protocol"]?.jsonPrimitive?.content?.lowercase() ?: continue
            val tag = o["tag"]?.jsonPrimitive?.content?.lowercase() ?: ""
            if (protocol in PROXY_PROTOCOLS && tag !in NON_PROXY_TAGS) {
                val settings = o["settings"]?.jsonObject
                val vnext = settings?.get("vnext")?.jsonArray?.firstOrNull()?.jsonObject
                if (vnext != null) {
                    val addr = vnext["address"]?.jsonPrimitive?.content
                    val port = vnext["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    if (!addr.isNullOrBlank() && port > 0) return addr to port
                }
                val servers = settings?.get("servers")?.jsonArray?.firstOrNull()?.jsonObject
                if (servers != null) {
                    val addr = servers["address"]?.jsonPrimitive?.content
                    val port = servers["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    if (!addr.isNullOrBlank() && port > 0) return addr to port
                }
            }
        }
        return null
    }
}
