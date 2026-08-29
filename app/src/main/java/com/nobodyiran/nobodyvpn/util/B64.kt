package com.nobodyiran.nobodyvpn.util

import java.util.Base64

object B64 {
    /** Tolerant base64 decoder: standard / URL-safe, padded or not, newlines stripped. */
    fun decode(input: String): ByteArray? {
        val clean = input.trim().replace("\n", "").replace("\r", "").replace(" ", "")
        if (clean.isEmpty()) return null
        val candidates = listOf(clean, clean + "=".repeat((4 - clean.length % 4) % 4))
        for (c in candidates) {
            for (mode in listOf(Base64.getUrlDecoder(), Base64.getDecoder(), Base64.getMimeDecoder())) {
                try {
                    return mode.decode(c)
                } catch (_: IllegalArgumentException) {
                }
            }
        }
        return null
    }

    fun decodeToString(input: String): String? =
        decode(input)?.let { String(it, Charsets.UTF_8) }

    fun encode(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    fun encodeUrlSafe(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
