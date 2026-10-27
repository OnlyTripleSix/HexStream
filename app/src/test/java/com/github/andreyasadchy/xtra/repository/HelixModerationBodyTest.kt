package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.ui.chat.TimeoutDuration
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class HelixModerationBodyTest {
    @Test fun timeoutPayloadContainsNumericSecondsAndPreservesTargetAndReason() {
        for ((input, expected) in mapOf(null to 600, "30" to 30, "30s" to 30, "5m" to 300,
            "2h" to 7200, "14d" to 1_209_600)) {
            val seconds = requireNotNull(TimeoutDuration.helixSeconds(input))
            val data = Json.parseToJsonElement(HelixModerationBody.ban("fixture_target", seconds,
                "fixture reason")).jsonObject.getValue("data").jsonObject
            assertEquals(expected, data.getValue("duration").jsonPrimitive.int)
            assertFalse(data.getValue("duration").jsonPrimitive.isString)
            assertEquals("fixture_target", data.getValue("user_id").jsonPrimitive.content)
            assertEquals("fixture reason", data.getValue("reason").jsonPrimitive.content)
        }
    }

    @Test fun explicitBanStillOmitsDuration() {
        val data = Json.parseToJsonElement(HelixModerationBody.ban("fixture_target", null, null))
            .jsonObject.getValue("data").jsonObject
        assertFalse(data.containsKey("duration"))
        assertEquals("fixture_target", data.getValue("user_id").jsonPrimitive.content)
    }

    @Test fun invalidNonNullDurationCannotSilentlyBecomeABan() {
        for (duration in listOf(0, -1, 1_209_601, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                HelixModerationBody.ban("fixture_target", duration, null)
            }
        }
    }
}
