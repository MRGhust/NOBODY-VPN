package com.nobodyiran.nobodyvpn.util

object C {
    const val TAG = "NobodyVPN"

    const val SOCKS_PORT = 10808
    const val LOCAL_DNS_PORT = 10853

    const val TAG_SOCKS = "socks-in"
    const val TAG_TUN = "tun"
    const val TAG_PROXY = "proxy"
    const val TAG_DIRECT = "direct"
    const val TAG_BLOCK = "block"
    const val TAG_DNS_OUT = "dns-out"
    const val TAG_REMOTE_DNS = "remote-dns"

    const val VPN_IPV4_CLIENT = "10.10.14.1"
    const val VPN_IPV6_CLIENT = "fd00::10:10:14:1"

    const val DEFAULT_MTU = 1500
    const val DEFAULT_VPN_DNS = "1.1.1.1"
    const val DEFAULT_REMOTE_DNS = "https://8.8.8.8/dns-query"
    const val DELAY_TEST_URL = "https://www.gstatic.com/generate_204"

    /** Minimal private/reserved ranges to route when LAN bypass is on. */
    val ROUTED_IP_LIST = listOf(
        "0.0.0.0/5", "8.0.0.0/7", "11.0.0.0/8", "12.0.0.0/6", "16.0.0.0/4",
        "32.0.0.0/3", "64.0.0.0/2", "128.0.0.0/3", "160.0.0.0/5", "168.0.0.0/6",
        "172.0.0.0/12", "172.32.0.0/11", "172.64.0.0/10", "172.128.0.0/9",
        "173.0.0.0/8", "174.0.0.0/7", "176.0.0.0/4", "192.0.0.0/9", "192.128.0.0/11",
        "192.160.0.0/13", "192.169.0.0/16", "192.170.0.0/15", "192.172.0.0/14",
        "192.176.0.0/12", "192.192.0.0/10", "193.0.0.0/8", "194.0.0.0/7",
        "196.0.0.0/6", "200.0.0.0/5", "208.0.0.0/4"
    )

    const val LINK_VLESS = "vless://"
    const val LINK_VMESS = "vmess://"
    const val LINK_TROJAN = "trojan://"
    const val LINK_SS = "ss://"

    val LINK_SCHEMES = listOf(LINK_VLESS, LINK_VMESS, LINK_TROJAN, LINK_SS)

    const val UA_DEFAULT = "NobodyVPN/1.0 (Android)"
}
