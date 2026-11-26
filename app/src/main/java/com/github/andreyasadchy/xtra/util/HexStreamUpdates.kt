package com.github.andreyasadchy.xtra.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Keep upstream update defaults from replacing this fork; preserve deliberate custom feeds. */
object HexStreamUpdates {
    const val DEFAULT_URL = "https://api.github.com/repos/ItsTripleSix/HexStream/releases?per_page=1"
    private const val LEGACY_URL = "https://api.github.com/repos/crackededed/xtra/releases/tags/latest"

    fun shouldOffer(tag: String?, installed: String, assetUpdated: Long, lastChecked: Long): Boolean {
        fun version(raw: String?): List<Long>? {
            val match = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-beta\\.(\\d+))?(?:-debug|-HEXSTREAM-QA)?$")
                .matchEntire(raw.orEmpty()) ?: return null
            val beta = match.groupValues[4].let { if (it.isEmpty()) Long.MAX_VALUE else it.toLongOrNull() ?: return null }
            return (1..3).map { match.groupValues[it].toLongOrNull() ?: return null } + beta
        }
        val offered = version(tag)
        val current = version(installed)
        if (offered != null && current != null) {
            for (index in offered.indices) {
                if (offered[index] != current[index]) return offered[index] > current[index]
            }
            return false
        }
        return assetUpdated > lastChecked
    }

    fun url(saved: String?): String = when {
        saved.isNullOrBlank() || saved.trim().trimEnd('/').equals(LEGACY_URL, ignoreCase = true) -> DEFAULT_URL
        else -> saved
    }

    /** GitHub's release list includes betas; custom single-release feeds remain compatible. */
    fun parse(raw: String): JsonObject = when (val value = Json.parseToJsonElement(raw)) {
        is JsonObject -> value
        is JsonArray -> value.filterIsInstance<JsonObject>()
            .firstOrNull { it["draft"]?.jsonPrimitive?.booleanOrNull != true } ?: JsonObject(emptyMap())
        else -> error("Invalid release response")
    }
}
