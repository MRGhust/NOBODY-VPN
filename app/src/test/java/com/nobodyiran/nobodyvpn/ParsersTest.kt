package com.nobodyiran.nobodyvpn

import com.nobodyiran.nobodyvpn.core.ConfigBuilder
import com.nobodyiran.nobodyvpn.data.Profile
import com.nobodyiran.nobodyvpn.data.ProfileType
import com.nobodyiran.nobodyvpn.data.RoutingPreset
import com.nobodyiran.nobodyvpn.data.Settings
import com.nobodyiran.nobodyvpn.parser.JsonConfig
import com.nobodyiran.nobodyvpn.parser.ShareLink
import com.nobodyiran.nobodyvpn.parser.SubParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {

    /** The user's real subscription config (VLESS + WS + TLS behind Cloudflare workers). */
    private val userConfig = """{
  "remarks": "💦 3. VLESS - IPv4 : 443",
  "log": {"loglevel": "warning"},
  "dns": {"servers": [{"address": "https://8.8.8.8/dns-query", "tag": "remote-dns"}], "queryStrategy": "UseIP", "tag": "dns"},
  "inbounds": [
    {"listen": "127.0.0.1", "port": 10808, "protocol": "mixed", "settings": {"auth": "noauth", "udp": true}, "sniffing": {"destOverride": ["http", "tls"], "enabled": true, "routeOnly": true}, "tag": "mixed-in"},
    {"listen": "127.0.0.1", "port": 10853, "protocol": "dokodemo-door", "settings": {"address": "1.1.1.1", "network": "tcp,udp", "port": 53}, "tag": "dns-in"}
  ],
  "outbounds": [
    {"protocol": "vless", "settings": {"vnext": [{"address": "104.21.40.32", "port": 443, "users": [{"id": "6f4e929b-6057-4a4c-9dc8-951468577c1a", "encryption": "none"}]}]}, "streamSettings": {"network": "ws", "wsSettings": {"host": "example.workers.dev", "path": "/vl/test?ed=2560"}, "security": "tls", "tlsSettings": {"serverName": "example.workers.dev", "fingerprint": "chrome", "alpn": ["http/1.1"]}}, "tag": "proxy"},
    {"protocol": "dns", "settings": {"rules": [{"action": "hijack"}]}, "tag": "dns-out"},
    {"protocol": "freedom", "settings": {"domainStrategy": "UseIP"}, "tag": "direct"},
    {"protocol": "blackhole", "settings": {"response": {"type": "http"}}, "tag": "block"}
  ],
  "routing": {"domainStrategy": "IPIfNonMatch", "rules": []},
  "policy": {"levels": {"0": {"connIdle": 300}}}
}"""

    @Test
    fun `parse full user config as CUSTOM profile`() {
        val p = JsonConfig.parse(userConfig)
        assertNotNull(p)
        assertEquals(ProfileType.CUSTOM, p!!.type)
        assertEquals("104.21.40.32", p.address)
        assertEquals(443, p.port)
        assertTrue(p.remark.contains("VLESS"))
    }

    @Test
    fun `build custom config injects tun and keeps user routing`() {
        val p = JsonConfig.parse(userConfig)!!
        val s = Settings()
        val json = ConfigBuilder.build(p, s, vpnMode = true)
        assertTrue(json.contains("\"protocol\": \"tun\""))
        assertTrue(json.contains("\"MTU\": 1500"))
        assertTrue(json.contains("104.21.40.32"))
        assertTrue(json.contains("\"dns-out\""))
        assertTrue(json.contains("\"stats\""))
    }

    @Test
    fun `parse vless link with ws tls`() {
        val link = "vless://6f4e929b-6057-4a4c-9dc8-951468577c1a@104.21.40.32:443" +
                "?type=ws&security=tls&host=example.workers.dev" +
                "&path=%2Fvl%2Ftest%3Fed%3D2560&sni=example.workers.dev&fp=chrome" +
                "&alpn=http%2F1.1&encryption=none#" +
                "%F0%9F%92%A6%20Test%20Server"
        val p = ShareLink.parse(link)
        assertNotNull(p)
        p!!
        assertEquals(ProfileType.VLESS, p.type)
        assertEquals("104.21.40.32", p.address)
        assertEquals(443, p.port)
        assertTrue(p.remark.contains("Test"))
        val o = p.outboundJson!!.replace(" ", "")
        assertTrue(o.contains("\"network\":\"ws\""))
        assertTrue(o.contains("/vl/test?ed=2560"))
        assertTrue(o.contains("\"serverName\":\"example.workers.dev\""))
        assertTrue(o.contains("\"fingerprint\":\"chrome\""))
    }

    @Test
    fun `parse vless reality link`() {
        val link = "vless://uuid123@1.2.3.4:8443?type=tcp&security=reality&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179&sni=www.microsoft.com&fp=chrome&flow=xtls-rprx-vision#Reality-Node"
        val p = ShareLink.parse(link)!!
        val o = p.outboundJson!!.replace(" ", "")
        assertTrue(o.contains("\"security\":\"reality\""))
        assertTrue(o.contains("\"publicKey\":\"SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc\""))
        assertTrue(o.contains("\"flow\":\"xtls-rprx-vision\""))
    }

    @Test
    fun `parse vmess base64 link`() {
        val vmessJson = """{"v":"2","ps":"VMess Node","add":"vm.example.com","port":"443","id":"a3482e88-686a-4a58-8126-99c9df64b7bf","aid":"0","scy":"auto","net":"ws","host":"cdn.example.com","path":"/vm","tls":"tls","sni":"vm.example.com","fp":"chrome"}"""
        val link = "vmess://" + java.util.Base64.getUrlEncoder().encodeToString(vmessJson.toByteArray())
        val p = ShareLink.parse(link)
        assertNotNull(p)
        p!!
        assertEquals(ProfileType.VMESS, p.type)
        assertEquals("vm.example.com", p.address)
        assertEquals(443, p.port)
        val o = p.outboundJson!!.replace(" ", "")
        assertTrue(o.contains("\"alterId\":0"))
        assertTrue(o.contains("\"security\":\"auto\""))
    }

    @Test
    fun `parse trojan link`() {
        val link = "trojan://pass-word%40x@example.com:443?security=tls&sni=example.com&type=tcp#Trojan1"
        val p = ShareLink.parse(link)!!
        assertEquals(ProfileType.TROJAN, p.type)
        val o = p.outboundJson!!.replace(" ", "")
        assertEquals("pass-word@x", o.substringAfter("\"password\":\"").substringBefore("\""))
        assertTrue(o.contains("\"serverName\":\"example.com\""))
    }

    @Test
    fun `parse shadowsocks sip002 and legacy`() {
        val sip002 = "ss://" + java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("aes-256-gcm:testpass".toByteArray()) + "@1.2.3.4:8388#SS-Node"
        val p1 = ShareLink.parse(sip002)!!
        assertEquals(ProfileType.SHADOWSOCKS, p1.type)
        val o1 = p1.outboundJson!!.replace(" ", "")
        assertEquals("aes-256-gcm", o1.substringAfter("\"method\":\"").substringBefore("\""))
        assertEquals("testpass", o1.substringAfter("\"password\":\"").substringBefore("\""))

        val legacyPlain = "aes-256-gcm:testpass2@5.6.7.8:8388"
        val legacy = "ss://" + java.util.Base64.getEncoder().encodeToString(legacyPlain.toByteArray()) + "#Legacy"
        val p2 = ShareLink.parse(legacy)!!
        assertEquals("5.6.7.8", p2.address)
        assertEquals(8388, p2.port)
    }

    @Test
    fun `subscription base64 blob decodes to links`() {
        val links = listOf(
            "vless://uuid@1.1.1.1:443?type=ws#A",
            "trojan://pw@2.2.2.2:443#B",
            "ss://" + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("aes-128-gcm:pw".toByteArray()) + "@3.3.3.3:443#C"
        )
        val blob = java.util.Base64.getEncoder().encodeToString(links.joinToString("\n").toByteArray())
        val r = SubParser.decode(blob)
        assertTrue(r is SubParser.Result.Links)
        assertEquals(3, (r as SubParser.Result.Links).links.size)
    }

    @Test
    fun `build generated config for vless with vpn mode`() {
        val link = "vless://uuid@1.2.3.4:443?type=ws&security=tls&path=%2Fws&host=h.com&sni=h.com#Gen"
        val p = ShareLink.parse(link)!!
        val s = Settings(routingPreset = RoutingPreset.BYPASS_IR, blockAds = true, blockUdp = true)
        val json = ConfigBuilder.build(p, s, vpnMode = true)
        assertTrue(json.contains("\"protocol\": \"tun\""))
        assertTrue(json.contains("10808"))
        assertTrue(json.contains("\"dns-out\""))
        assertTrue(json.contains("\"remote-dns\""))
        assertTrue(json.contains("geosite:category-ir"))
        assertTrue(json.contains("geoip:ir"))
        assertTrue(json.contains("geosite:category-ads-all"))
        assertTrue(json.contains("\"network\": \"udp\""))
        assertTrue(json.contains("tcp,udp"))
        assertTrue(json.contains("\"freedom\""))
        assertTrue(json.contains("\"blackhole\""))
        assertTrue(json.contains("statsOutboundUplink"))
    }

    @Test
    fun `export vless roundtrip`() {
        val link = "vless://uuid-xyz@1.2.3.4:443?type=grpc&security=reality&pbk=KEY123&sid=abcd&sni=x.com&fp=chrome#RT"
        val p = ShareLink.parse(link)!!
        val exported = ShareLink.export(p)!!
        val reparsed = ShareLink.parse(exported)!!
        assertEquals(p.address, reparsed.address)
        assertEquals(p.port, reparsed.port)
        val o = reparsed.outboundJson!!.replace(" ", "")
        assertTrue(o.contains("\"publicKey\":\"KEY123\""))
        assertTrue(o.contains("\"network\":\"grpc\""))
    }
}
