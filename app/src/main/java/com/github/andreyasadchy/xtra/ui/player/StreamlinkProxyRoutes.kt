package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.model.ui.CustomProxy
import com.github.andreyasadchy.xtra.model.ui.StreamProxy
import java.net.URI

internal object StreamlinkProxyRoutes {
    fun urls(custom: List<CustomProxy>, http: List<StreamProxy>): List<String> {
        val tunnels = http.filter { it.enabled }.sortedBy { it.position }.mapNotNull { proxy ->
            val host = proxy.host?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val port = proxy.port?.takeIf { it in 1..65535 } ?: return@mapNotNull null
            val userInfo = proxy.username?.takeIf { it.isNotEmpty() }?.let {
                "$it:${proxy.password.orEmpty()}"
            }
            runCatching { URI("httpproxy", userInfo, host, port, null, null, null).toASCIIString() }.getOrNull()
        }
        val playlists = custom.filter { it.enabled }.sortedBy { it.position }.mapNotNull { proxy ->
            val raw = proxy.url?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val value = if ("://" in raw) raw else "https://$raw"
            runCatching {
                URI(value).takeIf {
                    !it.host.isNullOrBlank() && it.scheme in listOf("https", "http", "httpproxy") &&
                        (it.port == -1 || it.port in 1..65535)
                }?.toASCIIString()?.replace("$" + "channel", "[channel]")
            }.getOrNull()
        }
        val (customTunnels, legacy) = playlists.partition { it.startsWith("httpproxy://") }
        return (customTunnels + tunnels + legacy).distinct()
    }
}
