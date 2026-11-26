package com.github.andreyasadchy.xtra.util

import com.github.andreyasadchy.xtra.model.ui.CustomProxy

object PlaybackProxyPresets {
    val hosts = listOf("eu2.luminous.dev", "eu.luminous.dev", "lb-na.cdn-perfprod.com",
        "lb-eu.cdn-perfprod.com", "lb-eu2.cdn-perfprod.com", "lb-as.cdn-perfprod.com")
    val urls = hosts.map { "https://$it/live/\$channel?allow_source=true&allow_audio_only=true&fast_bread=true" }
    val httpUrls = listOf("httpproxy://firefox.api.cdn-perfprod.com:2023",
        "httpproxy://chromium.api.cdn-perfprod.com:2023")

    fun isHttpTunnel(url: String?) = url?.trim()?.startsWith("httpproxy://", true) == true

    fun httpPresetIndex(url: String?) = httpUrls.indexOfFirst { it.equals(url?.trim(), true) }

    fun initialItems() = urls.mapIndexed { index, url -> CustomProxy(url, false, index, true) }

    /** Called by the global store once. Never revive an emptied/disabled list or replace a custom tunnel. */
    fun automaticImports(existing: List<CustomProxy>): List<CustomProxy> {
        if (existing.none { it.enabled } || existing.any { it.url?.trim()?.startsWith("httpproxy://", true) == true }) return emptyList()
        val next = (existing.maxOfOrNull { it.position } ?: -1) + 1
        return httpUrls.mapIndexed { index, url -> CustomProxy(url, false, next + index, true) }
    }

    fun imports(existing: List<CustomProxy>, legacy: List<CustomProxy>): List<CustomProxy> {
        val known = existing.mapNotNull { it.url }.toMutableSet()
        var position = (existing.maxOfOrNull { it.position } ?: -1) + 1
        return legacy.sortedBy { it.position }.mapNotNull { old ->
            old.url?.takeIf { it.isNotBlank() && known.add(it) }?.let {
                CustomProxy(it, old.addQueryParams, position++, old.enabled)
            }
        }
    }
}
