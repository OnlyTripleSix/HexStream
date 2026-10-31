package com.github.andreyasadchy.xtra.util

internal object WhisperHistoryHeaders {
    fun normalized(headers: Map<String, String>) = headers.mapKeys { (key, _) -> when {
        key.equals("Authorization", true) -> "Authorization"
        key.equals("Client-Id", true) -> "Client-Id"
        else -> key
    } }
    fun choose(base: Map<String, String>, verified: Map<String, String>, valid: Boolean): Map<String, String> {
        val selected = normalized(base)
        val cached = normalized(verified)
        return if (valid && selected["Authorization"]?.isNotBlank() == true && selected["Client-Id"]?.isNotBlank() == true &&
            cached["Authorization"] == selected["Authorization"] && cached["Client-Id"] == selected["Client-Id"] &&
            cached.any { it.key.equals("Client-Integrity", true) && it.value.isNotBlank() }) cached else selected
    }
}
