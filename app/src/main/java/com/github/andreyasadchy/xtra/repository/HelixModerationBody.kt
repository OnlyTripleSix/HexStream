package com.github.andreyasadchy.xtra.repository

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal object HelixModerationBody {
    fun ban(targetId: String?, duration: Int?, reason: String?): String {
        require(duration == null || duration in 1..1_209_600) { "Invalid timeout duration" }
        return buildJsonObject {
            putJsonObject("data") {
                duration?.let { put("duration", it) }
                put("reason", reason)
                put("user_id", targetId)
            }
        }.toString()
    }
}
