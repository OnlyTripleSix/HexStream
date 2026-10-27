package com.github.andreyasadchy.xtra.ui.chat

import org.junit.Assert.*
import org.junit.Test

class TimeoutDurationTest {
    @Test fun missingArgumentUsesTenMinutes() {
        assertEquals(600, TimeoutDuration.helixSeconds(null))
    }

    @Test fun plainSecondsAreNotReplacedByDefault() {
        for (seconds in listOf(1, 30, 601, 1_209_600)) {
            assertEquals(seconds, TimeoutDuration.helixSeconds(seconds.toString()))
        }
    }

    @Test fun unitSuffixesConvertToSeconds() {
        for ((input, expected) in mapOf("30s" to 30, "5m" to 300, "2h" to 7200, "14d" to 1_209_600,
            "30S" to 30, "5M" to 300, "2H" to 7200, "14D" to 1_209_600, " 5m " to 300)) {
            assertEquals(input, expected, TimeoutDuration.helixSeconds(input))
        }
    }

    @Test fun malformedInputCannotBecomeAPermanentBanOrDifferentTimeout() {
        for (input in listOf("", " ", "m", "5w", "1.5h", "1h30m", "-1", "+1", "1 m", "٣٠")) {
            assertNull(input, TimeoutDuration.helixSeconds(input))
        }
    }

    @Test fun invalidRangeAndIntegerOverflowAreRejected() {
        for (input in listOf("0", "0s", "1209601", "15d", "337h", "20161m", "2147483647d",
            "2147483647", "2147483648", "999999999999999999999999999999m")) {
            assertNull(input, TimeoutDuration.helixSeconds(input))
        }
    }
}
