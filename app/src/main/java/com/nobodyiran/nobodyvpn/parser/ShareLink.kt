package com.nobodyiran.nobodyvpn.parser

import com.nobodyiran.nobodyvpn.data.Profile
import com.nobodyiran.nobodyvpn.data.ProfileType
import com.nobodyiran.nobodyvpn.util.B64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.URLDecoder

/**
 * Parser / exporter for V2Ray share links:
 * vless:// vmess:// trojan:// ss://
 */
object ShareLink {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ------------------------------------------------------------------ parse

    fun parse(link: String): Profile? {
        val l = link.trim()
        return try {
            when {
                l.startsWith("vless://", ignoreCase = true) -> parseVless(l)
                l.startsWith("trojan://", ignoreCase = true) -> parseTrojan(l)
                l.startsWith("vmess://", ignoreCase = true) -> parseVmess(l)
                l.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(l)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private class ParsedUrl(
        val userinfo: String,
        val host: String,
        val port: Int,
        val params: Map<String, String>,
        val remark: String
    )

    private fun splitUrl(rest: String, defaultPort: Int): ParsedUrl {
        var body = rest
        var remark = ""
        val hashIdx = body.indexOf('#')
        if (hashIdx >= 0) {
            remark = urlDecode(body.substring(hashIdx + 1))
            body = body.substring(0, hashIdx)
        }
        var params = emptyMap<String, String>()
        val qIdx = body.indexOf('?')
        if (qIdx >= 0) {
            params = parseQuery(body.substring(qIdx + 1))
            body = body.substring(0, qIdx)
        }
        val atIdx = body.lastIndexOf('@')
        val userinfo: String
        val hostport: String
        if (atIdx >= 0) {
            userinfo = body.substring(0, atIdx)
            hostport = body.substring(atIdx + 1)
        } else {
            userinfo = ""
            hostport = body
        }
        val (host, port) = splitHostPort(hostport, defaultPort)
        return ParsedUrl(userinfo, host, port, params, remark)
    }

    private fun splitHostPort(hp: String, defaultPort: Int): Pair<String, Int> {
        return if (hp.startsWith("[")) {
            val close = hp.indexOf(']')
            if (close < 0) return hp to defaultPort
            val host = hp.substring(1, close)
            val rest = hp.substring(close + 1)
            val port = if (rest.startsWith(":")) rest.substring(1).toIntOrNull() ?: defaultPort else defaultPort
            host to port
        } else {
            val colon = hp.lastIndexOf(':')
            if (colon > 0) {
                val port = hp.substring(colon + 1).toIntOrNull()
                if (port != null) hp.substring(0, colon) to port else hp to defaultPort
            } else {
                hp to defaultPort
            }
        }
    }

    private fun parseQuery(q: String): Map<String, String> =
        q.split('&')
            .mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null
                else urlDecode(it.substring(0, i)) to urlDecode(it.substring(i + 1))
            }.toMap()

    private fun urlDecode(s: String): String =
        try {
            URLDecoder.decode(s, "UTF-8")
        } catch (_: Exception) {
            s
        }

    // ------------------------------------------------------------------ VLESS

    private fun parseVless(link: String): Profile? {
        val p = splitUrl(link.substring("vless://".length), 443)
        if (p.host.isBlank() || p.userinfo.isBlank()) return null
        val uuid = urlDecode(p.userinfo).substringBefore('/')
        val net = (p.params["type"] ?: "tcp").lowercase()
        val security = p.params["security"]?.lowercase() ?: "none"
        val ss = buildStreamSettings(net, security, p.params, p.host)
        val outbound = buildJsonObject {
            put("tag", "proxy")
            put("protocol", "vless")
            putJsonObject("settings") {
                putJsonArray("vnext") {
                    add(buildJsonObject {
                        put("address", p.host)
                        put("port", p.port)
                        putJsonArray("users") {
                            add(buildJsonObject {
                                put("id", uuid)
                                put("encryption", p.params["encryption"] ?: "none")
                                if (!p.params["flow"].isNullOrBlank()) put("flow", p.params["flow"]!!)
                                if (!p.params["level"].isNullOrBlank()) p.params["level"]!!.toIntOrNull()?.let { l -> put("level", l) }
                            })
                        }
                    })
                }
            }
            put("streamSettings", ss)
        }
        return Profile(
            remark = p.remark.ifBlank { "${p.host}:${p.port}" },
            type = ProfileType.VLESS,
            outboundJson = json.encodeToString(JsonObject.serializer(), outbound),
            address = p.host,
            port = p.port
        )
    }

    // ------------------------------------------------------------------ TROJAN

    private fun parseTrojan(link: String): Profile? {
        val p = splitUrl(link.substring("trojan://".length), 443)
        if (p.host.isBlank() || p.userinfo.isBlank()) return null
        val net = (p.params["type"] ?: "tcp").lowercase()
        val security = (p.params["security"]?.lowercase()).takeIf { !it.isNullOrBlank() } ?: "tls"
        val ss = buildStreamSettings(net, security, p.params, p.host)
        val outbound = buildJsonObject {
            put("tag", "proxy")
            put("protocol", "trojan")
            putJsonObject("settings") {
                putJsonArray("servers") {
                    add(buildJsonObject {
                        put("address", p.host)
                        put("port", p.port)
                        put("password", urlDecode(p.userinfo).substringBefore('/'))
                    })
                }
            }
            put("streamSettings", ss)
        }
        return Profile(
            remark = p.remark.ifBlank { "${p.host}:${p.port}" },
            type = ProfileType.TROJAN,
            outboundJson = json.encodeToString(JsonObject.serializer(), outbound),
            address = p.host,
            port = p.port
        )
    }

    // ------------------------------------------------------------------ VMESS

    private fun parseVmess(link: String): Profile? {
        val body = link.substring("vmess://".length).trim()
        // Some providers put a base64 vless-like URL after vmess://
        if (body.startsWith("vless://") || body.startsWith("trojan://")) {
            val inner = B64.decodeToString(body.substringAfter("://"))
            val fallback = inner?.let { parse(it) }
            if (fallback != null) return fallback.copy(type = ProfileType.VMESS)
        }
        val decoded = B64.decodeToString(body) ?: return null
        val obj = json.parseToJsonElement(decoded).jsonObject
        fun str(key: String): String = obj[key]?.toString()?.trim('"') ?: ""

        val host = str("add")
        val port = str("port").toIntOrNull() ?: 443
        if (host.isBlank()) return null

        val params = mutableMapOf<String, String>()
        params["type"] = str("net").ifBlank { "tcp" }
        if (str("tls") == "tls") params["security"] = "tls"
        if (str("sni").isNotBlank()) params["sni"] = str("sni")
        if (str("host").isNotBlank()) params["host"] = str("host")
        if (str("path").isNotBlank()) params["path"] = str("path")
        if (str("alpn").isNotBlank()) params["alpn"] = str("alpn")
        if (str("fp").isNotBlank()) params["fp"] = str("fp")
        if (str("type").isNotBlank() && params["type"] in listOf("tcp", "kcp", "quic")) params["headerType"] = str("type")

        val ss = buildStreamSettings(params["type"] ?: "tcp", params["security"] ?: "none", params, host)
        val outbound = buildJsonObject {
            put("tag", "proxy")
            put("protocol", "vmess")
            putJsonObject("settings") {
                putJsonArray("vnext") {
                    add(buildJsonObject {
                        put("address", host)
                        put("port", port)
                        putJsonArray("users") {
                            add(buildJsonObject {
                                put("id", str("id"))
                                put("alterId", str("aid").toIntOrNull() ?: 0)
                                put("security", str("scy").ifBlank { "auto" })
                            })
                        }
                    })
                }
            }
            put("streamSettings", ss)
        }
        return Profile(
            remark = str("ps").ifBlank { "$host:$port" },
            type = ProfileType.VMESS,
            outboundJson = json.encodeToString(JsonObject.serializer(), outbound),
            address = host,
            port = port
        )
    }

    // ------------------------------------------------------------------ SHADOWSOCKS

    private fun parseShadowsocks(link: String): Profile? {
        var body = link.substring("ss://".length)
        var remark = ""
        val hashIdx = body.indexOf('#')
        if (hashIdx >= 0) {
            remark = urlDecode(body.substring(hashIdx + 1))
            body = body.substring(0, hashIdx)
        }
        var params = emptyMap<String, String>()
        val qIdx = body.indexOf('?')
        if (qIdx >= 0) {
            params = parseQuery(body.substring(qIdx + 1))
            body = body.substring(0, qIdx)
        }

        // Plugins are not supported by the Xray shadowsocks outbound
        if (params["plugin"]?.isNotBlank() == true) return null

        var method: String
        var password: String
        var hostport: String

        if (body.contains('@')) {
            // SIP002: ss://base64(method:pass)@host:port
            val atIdx = body.lastIndexOf('@')
            val userinfo = body.substring(0, atIdx)
            hostport = body.substring(atIdx + 1)
            val decodedUser = B64.decodeToString(userinfo) ?: urlDecode(userinfo) ?: return null
            val sep = decodedUser.indexOf(':')
            if (sep <= 0) return null
            method = decodedUser.substring(0, sep)
            password = decodedUser.substring(sep + 1)
        } else {
            // Legacy: ss://base64(method:pass@host:port)
            val decoded = B64.decodeToString(body) ?: return null
            val atIdx = decoded.lastIndexOf('@')
            if (atIdx <= 0) return null
            val userinfo = decoded.substring(0, atIdx)
            hostport = decoded.substring(atIdx + 1)
            val sep = userinfo.indexOf(':')
            if (sep <= 0) return null
            method = userinfo.substring(0, sep)
            password = userinfo.substring(sep + 1)
        }

        val (host, port) = splitHostPort(hostport, 443)
        if (host.isBlank() || method.isBlank()) return null

        val outbound = buildJsonObject {
            put("tag", "proxy")
            put("protocol", "shadowsocks")
            putJsonObject("settings") {
                putJsonArray("servers") {
                    add(buildJsonObject {
                        put("address", host)
                        put("port", port)
                        put("method", method)
                        put("password", password)
                    })
                }
            }
        }
        return Profile(
            remark = remark.ifBlank { "$host:$port" },
            type = ProfileType.SHADOWSOCKS,
            outboundJson = json.encodeToString(JsonObject.serializer(), outbound),
            address = host,
            port = port
        )
    }

    // ------------------------------------------------------------------ stream settings

    private fun buildStreamSettings(
        network: String,
        security: String,
        params: Map<String, String>,
        serverHost: String
    ): JsonObject {
        val net = when (network) {
            "h2", "http" -> "h2"
            "splithttp" -> "xhttp"
            else -> network
        }
        val transport = buildJsonObject {
            when (net) {
                "ws" -> putJsonObject("settings") {
                    put("path", (params["path"] ?: "/").ifBlank { "/" })
                    params["host"]?.takeIf { it.isNotBlank() }?.let {
                        putJsonObject("headers") { put("Host", it) }
                    }
                }
                "grpc" -> putJsonObject("settings") {
                    put("serviceName", params["serviceName"] ?: params["path"] ?: "")
                    put("multiMode", false)
                }
                "h2" -> putJsonObject("settings") {
                    putJsonArray("host") { add(kotlinx.serialization.json.JsonPrimitive(params["host"] ?: "")) }
                    put("path", params["path"] ?: "/")
                }
                "httpupgrade" -> putJsonObject("settings") {
                    put("path", params["path"] ?: "/")
                    params["host"]?.takeIf { it.isNotBlank() }?.let { put("host", it) }
                }
                "xhttp" -> putJsonObject("settings") {
                    put("path", params["path"] ?: "/")
                    params["host"]?.takeIf { it.isNotBlank() }?.let { put("host", it) }
                    params["mode"]?.takeIf { it.isNotBlank() }?.let { put("mode", it) }
                }
                "kcp" -> putJsonObject("settings") {
                    params["seed"]?.takeIf { it.isNotBlank() }?.let { put("seed", it) }
                    putJsonObject("header") { put("type", params["headerType"] ?: "none") }
                }
                "quic" -> putJsonObject("settings") {
                    put("security", params["quicSecurity"] ?: "none")
                    put("key", params["key"] ?: "")
                    putJsonObject("header") { put("type", params["headerType"] ?: "none") }
                }
                "tcp" -> {
                    val headerType = params["headerType"] ?: "none"
                    if (headerType == "http") {
                        putJsonObject("settings") {
                            putJsonObject("header") {
                                put("type", "http")
                                putJsonObject("request") {
                                    putJsonArray("path") {
                                        add(kotlinx.serialization.json.JsonPrimitive(params["path"] ?: "/"))
                                    }
                                    putJsonArray("headers") {
                                        add(buildJsonObject {
                                            put("Host", buildJsonArray {
                                                add(kotlinx.serialization.json.JsonPrimitive(params["host"] ?: serverHost))
                                            })
                                        })
                                    }
                                }
                            }
                        }
                    } else {
                        putJsonObject("settings") {
                            putJsonObject("header") { put("type", "none") }
                        }
                    }
                }
            }
        }

        val tls = when (security) {
            "tls", "reality" -> buildJsonObject {
                val sni = (params["sni"] ?: params["host"] ?: serverHost).ifBlank { serverHost }
                put("serverName", sni)
                params["fp"]?.takeIf { it.isNotBlank() }?.let { put("fingerprint", it) }
                params["alpn"]?.takeIf { it.isNotBlank() }?.let {
                    putJsonArray("alpn") { it.split(",").forEach { a -> add(kotlinx.serialization.json.JsonPrimitive(a.trim())) } }
                }
                if (security == "reality") {
                    put("publicKey", params["pbk"] ?: "")
                    params["sid"]?.takeIf { it.isNotBlank() }?.let { put("shortId", it) }
                    params["spx"]?.takeIf { it.isNotBlank() }?.let { put("spiderX", it) }
                } else {
                    val insecure = params["allowInsecure"] == "1" || params["allowInsecure"] == "true" ||
                            params["insecure"] == "1" || params["insecure"] == "true"
                    if (insecure) put("allowInsecure", true)
                }
            }
            else -> null
        }

        return buildJsonObject {
            put("network", net)
            if (security == "reality") put("security", "reality")
            else if (security == "tls") put("security", "tls")
            else put("security", "none")
            if (transport.isNotEmpty()) put("settings", transport["settings"]!!.jsonObject)
            tls?.let { put(security + "Settings", it) }
        }
    }

    // ------------------------------------------------------------------ export

    fun export(profile: Profile): String? {
        if (profile.type == ProfileType.CUSTOM) return null
        val outboundStr = profile.outboundJson ?: return null
        return try {
            val o = json.parseToJsonElement(outboundStr).jsonObject
            when (profile.type) {
                ProfileType.VLESS -> exportVless(o, profile)
                ProfileType.VMESS -> exportVmess(o, profile)
                ProfileType.TROJAN -> exportTrojan(o, profile)
                ProfileType.SHADOWSOCKS -> exportSs(o, profile)
                ProfileType.CUSTOM -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun streamParams(o: JsonObject): Triple<String, String, JsonObject> {
        val ss = o["streamSettings"]?.jsonObject
        val network = ss?.get("network")?.toString()?.trim('"') ?: "tcp"
        val security = ss?.get("security")?.toString()?.trim('"') ?: "none"
        return Triple(network, security, ss ?: JsonObject(emptyMap()))
    }

    private fun tlsQuery(ss: JsonObject): String {
        val q = StringBuilder()
        val tlsKey = if (ss.containsKey("realitySettings")) "realitySettings" else "tlsSettings"
        val tls = ss[tlsKey]?.jsonObject
        if (tls != null) {
            if (tlsKey == "realitySettings") q.append("security=reality") else q.append("security=tls")
            tls["serverName"]?.let { q.append("&sni=").append(it.toString().trim('"')) }
            tls["fingerprint"]?.let { q.append("&fp=").append(it.toString().trim('"')) }
            tls["alpn"]?.let { arr ->
                val v = arr.toString().trim('[', ']').replace("\"", "").replace(",", "%2C")
                if (v.isNotBlank()) q.append("&alpn=").append(v)
            }
            if (tlsKey == "realitySettings") {
                tls["publicKey"]?.let { q.append("&pbk=").append(it.toString().trim('"')) }
                tls["shortId"]?.let { q.append("&sid=").append(it.toString().trim('"')) }
                tls["spiderX"]?.let { q.append("&spx=").append(it.toString().trim('"')) }
            } else if (tls["allowInsecure"]?.toString() == "true") {
                q.append("&allowInsecure=1")
            }
        } else {
            q.append("security=none")
        }
        return q.toString()
    }

    private fun transportQuery(network: String, ss: JsonObject): String {
        val t = ss["settings"]?.jsonObject
        val sb = StringBuilder()
        when (network) {
            "ws" -> {
                sb.append("type=ws")
                t?.get("path")?.let { sb.append("&path=").append(java.net.URLEncoder.encode(it.toString().trim('"'), "UTF-8")) }
                (t?.get("headers")?.jsonObject?.get("Host"))?.let { sb.append("&host=").append(java.net.URLEncoder.encode(it.toString().trim('"'), "UTF-8")) }
            }
            "grpc" -> {
                sb.append("type=grpc")
                t?.get("serviceName")?.let { sb.append("&serviceName=").append(it.toString().trim('"')) }
            }
            "h2" -> {
                sb.append("type=h2")
                (t?.get("host")?.let { h ->
                    h.toString().trim('[', ']').replace("\"", "")
                })?.let { sb.append("&host=").append(it) }
                t?.get("path")?.let { sb.append("&path=").append(java.net.URLEncoder.encode(it.toString().trim('"'), "UTF-8")) }
            }
            "httpupgrade" -> {
                sb.append("type=httpupgrade")
                t?.get("path")?.let { sb.append("&path=").append(java.net.URLEncoder.encode(it.toString().trim('"'), "UTF-8")) }
                t?.get("host")?.let { sb.append("&host=").append(java.net.URLEncoder.encode(it.toString().trim('"'), "UTF-8")) }
            }
            "xhttp" -> {
                sb.append("type=xhttp")
                t?.get("path")?.let { sb.append("&path=").append(java.net.URLEncoder.encode(it.toString().trim('"'), "UTF-8")) }
                t?.get("host")?.let { sb.append("&host=").append(java.net.URLEncoder.encode(it.toString().trim('"'), "UTF-8")) }
                t?.get("mode")?.let { sb.append("&mode=").append(it.toString().trim('"')) }
            }
            "kcp" -> {
                sb.append("type=kcp")
                t?.get("seed")?.let { sb.append("&seed=").append(it.toString().trim('"')) }
                (t?.get("header")?.jsonObject?.get("type"))?.let {
                    if (it.toString().trim('"') != "none") sb.append("&headerType=").append(it.toString().trim('"'))
                }
            }
            "quic" -> {
                sb.append("type=quic")
                t?.get("security")?.let { sb.append("&quicSecurity=").append(it.toString().trim('"')) }
                t?.get("key")?.let { sb.append("&key=").append(it.toString().trim('"')) }
            }
            "tcp" -> {
                val headerType = t?.get("header")?.jsonObject?.get("type")?.toString()?.trim('"') ?: "none"
                sb.append("type=tcp")
                if (headerType == "http") {
                    sb.append("&headerType=http")
                    (t?.get("header")?.jsonObject?.get("request")?.jsonObject?.get("path"))?.let {
                        val v = it.toString().trim('[', ']').replace("\"", "")
                        if (v.isNotBlank()) sb.append("&path=").append(java.net.URLEncoder.encode(v, "UTF-8"))
                    }
                    (t?.get("header")?.jsonObject?.get("request")?.jsonObject?.get("headers")?.jsonObject?.get("Host"))?.let {
                        val v = it.toString().replace("[", "").replace("]", "").replace("\"", "").trim()
                        if (v.isNotBlank()) sb.append("&host=").append(java.net.URLEncoder.encode(v, "UTF-8"))
                    }
                }
            }
            else -> sb.append("type=").append(network)
        }
        return sb.toString()
    }

    private fun exportVless(o: JsonObject, profile: Profile): String {
        val (network, _, ss) = streamParams(o)
        val vnext = o["settings"]?.jsonObject?.get("vnext")?.let { it as kotlinx.serialization.json.JsonArray }?.firstOrNull()?.jsonObject
        val user = vnext?.get("users")?.let { it as kotlinx.serialization.json.JsonArray }?.firstOrNull()?.jsonObject
        val id = user?.get("id")?.toString()?.trim('"') ?: ""
        val flow = user?.get("flow")?.toString()?.trim('"') ?: ""
        val enc = user?.get("encryption")?.toString()?.trim('"') ?: "none"
        val q = StringBuilder(transportQuery(network, ss))
        q.append('&').append(tlsQuery(ss))
        if (flow.isNotBlank()) q.append("&flow=").append(flow)
        if (enc != "none") q.append("&encryption=").append(enc)
        val frag = java.net.URLEncoder.encode(profile.remark, "UTF-8")
        return "vless://$id@${hostFor(profile)}:${profile.port}?$q#$frag"
    }

    private fun exportTrojan(o: JsonObject, profile: Profile): String {
        val (network, _, ss) = streamParams(o)
        val server = o["settings"]?.jsonObject?.get("servers")?.let { it as kotlinx.serialization.json.JsonArray }?.firstOrNull()?.jsonObject
        val password = server?.get("password")?.toString()?.trim('"') ?: ""
        val q = StringBuilder(transportQuery(network, ss))
        q.append('&').append(tlsQuery(ss))
        val frag = java.net.URLEncoder.encode(profile.remark, "UTF-8")
        return "trojan://${java.net.URLEncoder.encode(password, "UTF-8")}@${hostFor(profile)}:${profile.port}?$q#$frag"
    }

    private fun exportVmess(o: JsonObject, profile: Profile): String {
        val (network, _, ss) = streamParams(o)
        val vnext = o["settings"]?.jsonObject?.get("vnext")?.let { it as kotlinx.serialization.json.JsonArray }?.firstOrNull()?.jsonObject
        val user = vnext?.get("users")?.let { it as kotlinx.serialization.json.JsonArray }?.firstOrNull()?.jsonObject
        val tls = ss["tlsSettings"]?.jsonObject ?: ss["realitySettings"]?.jsonObject
        val obj = buildJsonObject {
            put("v", "2")
            put("ps", profile.remark)
            put("add", profile.address)
            put("port", profile.port.toString())
            put("id", user?.get("id")?.toString()?.trim('"') ?: "")
            put("aid", user?.get("alterId")?.toString()?.trim('"') ?: "0")
            put("scy", user?.get("security")?.toString()?.trim('"') ?: "auto")
            put("net", network)
            put("type", "none")
            (ss["settings"]?.jsonObject?.get("headers")?.jsonObject?.get("Host"))?.let { put("host", it.toString().trim('"')) }
            ss["settings"]?.jsonObject?.get("path")?.let { put("path", it.toString().trim('"')) }
            if (ss["security"]?.toString()?.trim('"') == "tls") put("tls", "tls")
            tls?.get("serverName")?.let { put("sni", it.toString().trim('"')) }
            tls?.get("fingerprint")?.let { put("fp", it.toString().trim('"')) }
            tls?.get("alpn")?.let { arr -> put("alpn", arr.toString().trim('[', ']').replace("\"", "")) }
        }
        return "vmess://" + B64.encode(json.encodeToString(JsonObject.serializer(), obj).toByteArray(Charsets.UTF_8))
    }

    private fun exportSs(o: JsonObject, profile: Profile): String {
        val server = o["settings"]?.jsonObject?.get("servers")?.let { it as kotlinx.serialization.json.JsonArray }?.firstOrNull()?.jsonObject
        val method = server?.get("method")?.toString()?.trim('"') ?: ""
        val password = server?.get("password")?.toString()?.trim('"') ?: ""
        val userinfo = B64.encodeUrlSafe("$method:$password".toByteArray(Charsets.UTF_8))
        val frag = java.net.URLEncoder.encode(profile.remark, "UTF-8")
        return "ss://$userinfo@${hostFor(profile)}:${profile.port}#$frag"
    }

    private fun hostFor(profile: Profile): String =
        if (profile.address.contains(':') && !profile.address.startsWith("[")) "[${profile.address}]" else profile.address
}
