package com.github.andreyasadchy.xtra.util

import com.github.andreyasadchy.xtra.model.ui.CustomProxy

object PlaybackProxyPresets {
    val hosts = listOf("eu2.luminous.dev", "eu.luminous.dev", "lb-as.cdn-perfprod.com")
    val urls = hosts.map { "https://$it/live/\$channel?allow_source=true&allow_audio_only=true&fast_bread=true" }

    fun initialItems() = urls.mapIndexed { index, url -> CustomProxy(url, false, index, true) }

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
