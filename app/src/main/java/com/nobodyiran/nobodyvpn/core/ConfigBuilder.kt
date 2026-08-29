package com.nobodyiran.nobodyvpn.core

import com.nobodyiran.nobodyvpn.data.Profile
import com.nobodyiran.nobodyvpn.data.ProfileType
import com.nobodyiran.nobodyvpn.data.RoutingPreset
import com.nobodyiran.nobodyvpn.data.Settings
import com.nobodyiran.nobodyvpn.util.C
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Builds the final Xray configuration JSON from a profile + user settings.
 */
object ConfigBuilder {

    private val json = Json { prettyPrint = true; encodeDefaults = true; isLenient = true }

    private val PROXY_PROTOCOLS = setOf("vless", "vmess", "trojan", "shadowsocks", "wireguard", "loopback")
    private val NON_PROXY_TAGS = setOf("direct", "block", "blocked", "dns-out", "dns", "api")

    fun build(profile: Profile, settings: Settings, vpnMode: Boolean): String {
        return if (profile.type == ProfileType.CUSTOM && profile.fullConfigJson != null) {
            buildCustom(profile, settings, vpnMode)
        } else {
            buildFromOutbound(profile, settings, vpnMode)
        }
    }

    // ------------------------------------------------------------------ normal profiles

    private fun buildFromOutbound(profile: Profile, s: Settings, vpnMode: Boolean): String {
        val proxyOutbound = profile.outboundJson?.let {
            runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull()
        } ?: return errorConfig(s)
        val tagged = JsonObject(proxyOutbound.toMutableMap().apply {
            put("tag", JsonPrimitive(C.TAG_PROXY))
        })

        val dnsEnabled = s.customDnsEnabled
        val rules = buildRules(s, vpnMode, dnsEnabled)

        val config = buildJsonObject {
            put("log", buildJsonObject { put("loglevel", s.logLevel) })
            put("remarks", profile.remark)
            if (dnsEnabled) {
                put("dns", buildDns(s))
            }
            putJsonArray("inbounds") {
                add(buildSocksInbound())
                if (vpnMode) add(buildTunInbound(s.mtu))
            }
            putJsonArray("outbounds") {
                add(tagged)
                if (dnsEnabled) add(buildDnsOutbound())
                add(buildDirectOutbound())
                add(buildBlockOutbound())
            }
            put("routing", buildJsonObject {
                put("domainStrategy", s.routingDomainStrategy)
                putJsonArray("rules") { rules.forEach { add(it) } }
            })
            put("policy", buildPolicy())
            put("stats", buildJsonObject { })
        }
        return json.encodeToString(JsonObject.serializer(), config)
    }

    private fun errorConfig(s: Settings): String = buildJsonObject {
        put("log", buildJsonObject { put("loglevel", "warning") })
        putJsonArray("inbounds") { }
        putJsonArray("outbounds") { }
    }.let { json.encodeToString(JsonObject.serializer(), it) }

    private fun buildSocksInbound(): JsonObject = buildJsonObject {
        put("tag", C.TAG_SOCKS)
        put("listen", "127.0.0.1")
        put("port", C.SOCKS_PORT)
        put("protocol", "mixed")
        put("settings", buildJsonObject {
            put("auth", "noauth")
            put("udp", true)
        })
        put("sniffing", buildJsonObject {
            put("enabled", true)
            putJsonArray("destOverride") {
                add(JsonPrimitive("http")); add(JsonPrimitive("tls")); add(JsonPrimitive("quic"))
            }
        })
    }

    private fun buildTunInbound(mtu: Int): JsonObject = buildJsonObject {
        put("tag", C.TAG_TUN)
        put("protocol", "tun")
        put("settings", buildJsonObject {
            put("name", "xray0")
            put("MTU", mtu)
        })
        put("sniffing", buildJsonObject {
            put("enabled", true)
            putJsonArray("destOverride") {
                add(JsonPrimitive("http")); add(JsonPrimitive("tls")); add(JsonPrimitive("quic"))
            }
        })
    }

    private fun buildDns(s: Settings): JsonObject = buildJsonObject {
        putJsonArray("servers") {
            add(buildJsonObject {
                put("address", s.remoteDns)
                put("tag", C.TAG_REMOTE_DNS)
            })
        }
        put("queryStrategy", s.dnsQueryStrategy)
        put("tag", "dns")
    }

    private fun buildDnsOutbound(): JsonObject = buildJsonObject {
        put("protocol", "dns")
        put("tag", C.TAG_DNS_OUT)
        put("settings", buildJsonObject {
            putJsonArray("rules") {
                add(buildJsonObject { put("action", "hijack") })
            }
        })
    }

    private fun buildDirectOutbound(): JsonObject = buildJsonObject {
        put("protocol", "freedom")
        put("tag", C.TAG_DIRECT)
        put("settings", buildJsonObject { put("domainStrategy", "UseIP") })
    }

    private fun buildBlockOutbound(): JsonObject = buildJsonObject {
        put("protocol", "blackhole")
        put("tag", C.TAG_BLOCK)
        put("settings", buildJsonObject {
            put("response", buildJsonObject { put("type", "http") })
        })
    }

    private fun buildPolicy(): JsonObject = buildJsonObject {
        putJsonObject("levels") {
            putJsonObject("8") {
                put("handshake", 4)
                put("connIdle", 300)
                put("uplinkOnly", 1)
                put("downlinkOnly", 1)
            }
        }
        putJsonObject("system") {
            put("statsOutboundUplink", true)
            put("statsOutboundDownlink", true)
        }
    }

    private fun buildRules(s: Settings, vpnMode: Boolean, dnsEnabled: Boolean): List<JsonObject> {
        val rules = mutableListOf<JsonObject>()

        if (dnsEnabled) {
            rules.add(buildJsonObject {
                putJsonArray("inboundTag") {
                    add(JsonPrimitive(C.TAG_SOCKS))
                    if (vpnMode) add(JsonPrimitive(C.TAG_TUN))
                }
                put("port", "53")
                put("outboundTag", C.TAG_DNS_OUT)
                put("type", "field")
            })
            rules.add(buildJsonObject {
                putJsonArray("inboundTag") { add(JsonPrimitive(C.TAG_REMOTE_DNS)) }
                put("outboundTag", C.TAG_PROXY)
                put("type", "field")
            })
            rules.add(buildJsonObject {
                putJsonArray("inboundTag") { add(JsonPrimitive("dns")) }
                put("outboundTag", C.TAG_DIRECT)
                put("type", "field")
            })
        }

        // Always keep private / LAN traffic direct
        rules.add(buildJsonObject {
            putJsonArray("domain") { add(JsonPrimitive("geosite:private")) }
            put("outboundTag", C.TAG_DIRECT)
            put("type", "field")
        })
        rules.add(buildJsonObject {
            putJsonArray("ip") { add(JsonPrimitive("geoip:private")) }
            put("outboundTag", C.TAG_DIRECT)
            put("type", "field")
        })

        if (s.routingPreset == RoutingPreset.BYPASS_IR) {
            rules.add(buildJsonObject {
                putJsonArray("domain") { add(JsonPrimitive("geosite:category-ir")) }
                put("outboundTag", C.TAG_DIRECT)
                put("type", "field")
            })
            rules.add(buildJsonObject {
                putJsonArray("ip") { add(JsonPrimitive("geoip:ir")) }
                put("outboundTag", C.TAG_DIRECT)
                put("type", "field")
            })
        }

        if (s.blockAds) {
            rules.add(buildJsonObject {
                putJsonArray("domain") { add(JsonPrimitive("geosite:category-ads-all")) }
                put("outboundTag", C.TAG_BLOCK)
                put("type", "field")
            })
        }

        // Custom user rules
        if (s.customDirectDomains.isNotEmpty()) {
            rules.add(domainIpRule(s.customDirectDomains, C.TAG_DIRECT))
        }
        if (s.customProxyDomains.isNotEmpty()) {
            rules.add(domainIpRule(s.customProxyDomains, C.TAG_PROXY))
        }
        if (s.customBlockDomains.isNotEmpty()) {
            rules.add(domainIpRule(s.customBlockDomains, C.TAG_BLOCK))
        }

        if (s.blockUdp) {
            rules.add(buildJsonObject {
                put("network", "udp")
                put("outboundTag", C.TAG_BLOCK)
                put("type", "field")
            })
        }

        // Catch-all -> proxy
        rules.add(buildJsonObject {
            put("network", "tcp,udp")
            put("outboundTag", C.TAG_PROXY)
            put("type", "field")
        })
        return rules
    }

    private fun domainIpRule(entries: List<String>, tag: String): JsonObject {
        val domains = entries.filter { !looksLikeIp(it) }
        val ips = entries.filter { looksLikeIp(it) }
        return buildJsonObject {
            if (domains.isNotEmpty()) putJsonArray("domain") { domains.forEach { add(JsonPrimitive(it)) } }
            if (ips.isNotEmpty()) putJsonArray("ip") { ips.forEach { add(JsonPrimitive(it)) } }
            put("outboundTag", tag)
            put("type", "field")
        }
    }

    private fun looksLikeIp(v: String): Boolean {
        val t = v.trim()
        if (t.contains('/')) return true
        if (t.contains(':')) return true // IPv6
        val parts = t.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) return true
        return false
    }

    // ------------------------------------------------------------------ custom config

    private fun buildCustom(profile: Profile, s: Settings, vpnMode: Boolean): String {
        return try {
            val root = json.parseToJsonElement(profile.fullConfigJson!!).jsonObject
            val mutable = root.toMutableMap()

            // log level
            mutable["log"] = buildJsonObject { put("loglevel", s.logLevel) }

            // stats + policy for speed display
            mutable["stats"] = buildJsonObject { }
            val existingPolicy = mutable["policy"] as? JsonObject
            mutable["policy"] = if (existingPolicy != null) {
                val pm = existingPolicy.toMutableMap()
                val sys = (pm["system"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
                sys["statsOutboundUplink"] = JsonPrimitive(true)
                sys["statsOutboundDownlink"] = JsonPrimitive(true)
                pm["system"] = JsonObject(sys)
                JsonObject(pm)
            } else {
                buildPolicy()
            }

            // Ensure a usable socks inbound on our port
            val inbounds = (mutable["inbounds"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
            val mutableInbounds = inbounds.toMutableList()
            val hasSocks = mutableInbounds.any {
                val proto = it["protocol"]?.jsonPrimitive?.content?.lowercase() ?: ""
                (proto == "socks" || proto == "mixed") && portOf(it) == C.SOCKS_PORT
            }
            if (!hasSocks) {
                mutableInbounds.add(
                    JsonObject(buildSocksInbound().toMutableMap().apply {
                        // unique tag to avoid collision
                        put("tag", JsonPrimitive("socks-in-extra"))
                    })
                )
            }
            if (vpnMode) {
                val hasTun = mutableInbounds.any { it["protocol"]?.jsonPrimitive?.content?.lowercase() == "tun" }
                if (!hasTun) mutableInbounds.add(buildTunInbound(s.mtu))
            }
            mutable["inbounds"] = JsonArray(mutableInbounds)

            // Put a real proxy outbound first so unmatched traffic goes through it
            val outbounds = (mutable["outbounds"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
            val mutableOutbounds = outbounds.toMutableList()
            val proxyIdx = mutableOutbounds.indexOfFirst {
                val proto = it["protocol"]?.jsonPrimitive?.content?.lowercase() ?: ""
                val tag = it["tag"]?.jsonPrimitive?.content?.lowercase() ?: ""
                proto in PROXY_PROTOCOLS && tag !in NON_PROXY_TAGS
            }
            if (proxyIdx > 0) {
                val proxy = mutableOutbounds.removeAt(proxyIdx)
                mutableOutbounds.add(0, proxy)
            }

            // Inject DNS machinery only when the config has none and the user wants it
            val hasDns = mutable.containsKey("dns")
            if (!hasDns && s.customDnsEnabled) {
                mutable["dns"] = buildDns(s)
                val hasDnsOut = mutableOutbounds.any { it["protocol"]?.jsonPrimitive?.content == "dns" }
                if (!hasDnsOut) mutableOutbounds.add(buildDnsOutbound())

                val dnsRules = listOf(
                    buildJsonObject {
                        putJsonArray("inboundTag") {
                            add(JsonPrimitive(C.TAG_SOCKS))
                            if (vpnMode) add(JsonPrimitive(C.TAG_TUN))
                        }
                        put("port", "53")
                        put("outboundTag", C.TAG_DNS_OUT)
                        put("type", "field")
                    },
                    buildJsonObject {
                        putJsonArray("inboundTag") { add(JsonPrimitive(C.TAG_REMOTE_DNS)) }
                        put("outboundTag", C.TAG_PROXY)
                        put("type", "field")
                    },
                    buildJsonObject {
                        putJsonArray("inboundTag") { add(JsonPrimitive("dns")) }
                        put("outboundTag", C.TAG_DIRECT)
                        put("type", "field")
                    }
                )
                val routingObj = (mutable["routing"] as? JsonObject)?.toMutableMap()
                if (routingObj == null) {
                    mutable["routing"] = buildJsonObject {
                        put("domainStrategy", s.routingDomainStrategy)
                        putJsonArray("rules") {
                            dnsRules.forEach { add(it) }
                            add(buildJsonObject {
                                putJsonArray("domain") { add(JsonPrimitive("geosite:private")) }
                                put("outboundTag", C.TAG_DIRECT)
                                put("type", "field")
                            })
                            add(buildJsonObject {
                                putJsonArray("ip") { add(JsonPrimitive("geoip:private")) }
                                put("outboundTag", C.TAG_DIRECT)
                                put("type", "field")
                            })
                            add(buildJsonObject {
                                put("network", "tcp,udp")
                                put("outboundTag", C.TAG_PROXY)
                                put("type", "field")
                            })
                        }
                    }
                } else {
                    val existingRules = (routingObj["rules"] as? JsonArray)?.toList() ?: emptyList()
                    routingObj["rules"] = JsonArray(dnsRules + existingRules)
                    mutable["routing"] = JsonObject(routingObj)
                }
            }

            mutable["outbounds"] = JsonArray(mutableOutbounds)

            json.encodeToString(JsonObject.serializer(), JsonObject(mutable))
        } catch (e: Exception) {
            // Fall back to the raw config; better than nothing
            profile.fullConfigJson!!
        }
    }

    private fun portOf(inbound: JsonObject): Int? =
        inbound["port"]?.let { p ->
            (p as? JsonPrimitive)?.let { it.content.toIntOrNull() }
        }
}
