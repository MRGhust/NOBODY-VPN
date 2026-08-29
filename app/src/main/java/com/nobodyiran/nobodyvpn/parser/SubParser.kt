package com.nobodyiran.nobodyvpn.parser

import com.nobodyiran.nobodyvpn.util.B64
import com.nobodyiran.nobodyvpn.util.C

/**
 * Decodes subscription payloads (base64 blob, plain link list, or a single JSON config).
 */
object SubParser {

    sealed class Result {
        data class Links(val links: List<String>) : Result()
        data class Config(val json: String) : Result()
        object Invalid : Result()
    }

    fun decode(content: String): Result {
        val text = content.trim()
        if (text.isEmpty()) return Result.Invalid

        if (text.startsWith("{") && text.contains("\"outbounds\"")) {
            return Result.Config(text)
        }

        // If content already contains share links line by line
        val lower = text.lowercase()
        val hasLinks = C.LINK_SCHEMES.any { lower.contains(it) }

        val lines = if (hasLinks) {
            text.lines()
        } else {
            val decoded = B64.decodeToString(text)
                ?: return Result.Invalid
            decoded.lines()
        }

        val links = lines
            .map { it.trim() }
            .filter { l -> C.LINK_SCHEMES.any { l.lowercase().startsWith(it) } }
            .distinct()
        if (links.isEmpty()) return Result.Invalid
        return Result.Links(links)
    }
}
