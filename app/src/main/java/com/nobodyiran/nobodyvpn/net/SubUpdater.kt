package com.nobodyiran.nobodyvpn.net

import com.nobodyiran.nobodyvpn.parser.JsonConfig
import com.nobodyiran.nobodyvpn.parser.ShareLink
import com.nobodyiran.nobodyvpn.parser.SubParser
import com.nobodyiran.nobodyvpn.util.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches and decodes subscriptions.
 */
object SubUpdater {

    data class FetchResult(val ok: Boolean, val content: String?, val message: String?)

    suspend fun fetch(url: String, userAgent: String): FetchResult = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", userAgent.ifBlank { C.UA_DEFAULT })
            conn.setRequestProperty("Accept", "*/*")
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                FetchResult(true, body, null)
            } else {
                FetchResult(false, null, "HTTP $code")
            }
        } catch (e: Exception) {
            FetchResult(false, null, e.message ?: e.javaClass.simpleName)
        }
    }

    /** Turns raw subscription content into profiles. [subscriptionId] tags every profile. */
    fun toProfiles(content: String, subscriptionId: String?): List<com.nobodyiran.nobodyvpn.data.Profile> {
        return when (val r = SubParser.decode(content)) {
            is SubParser.Result.Links -> r.links.mapNotNull { ShareLink.parse(it) }
                .map { it.copy(subscriptionId = subscriptionId) }
            is SubParser.Result.Config -> {
                val p = JsonConfig.parse(r.json)
                if (p != null) listOf(p.copy(subscriptionId = subscriptionId)) else emptyList()
            }
            SubParser.Result.Invalid -> emptyList()
        }
    }
}
