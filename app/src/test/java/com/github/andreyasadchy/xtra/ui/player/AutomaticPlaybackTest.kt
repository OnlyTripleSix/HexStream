package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.*
import org.junit.Test

class AutomaticPlaybackTest {
    @Test fun freshOrUnknownSettingsChooseAutomaticAndStandardIsExplicit() {
        assertTrue(AutomaticPlayback.enabled(null))
        assertTrue(AutomaticPlayback.enabled("automatic"))
        assertTrue(AutomaticPlayback.enabled("unrecognized"))
        assertFalse(AutomaticPlayback.enabled("standard"))
    }

    @Test fun automaticNeverFallsBackToUnfilteredDirectPlayback() {
        assertFalse(AutomaticPlayback.allowDirect(true, true))
        assertFalse(AutomaticPlayback.allowDirect(true, false))
        assertFalse(AutomaticPlayback.allowDirect(false, false))
        assertTrue(AutomaticPlayback.allowDirect(false, true))
    }

    @Test fun recoveryStopsAfterABoundedNumberOfAttemptsAndCanBeRetried() {
        val recovery = AutomaticPlayback.Recovery()
        var elapsed = 0L
        var attempts = 0
        while (true) {
            val delay = recovery.nextDelayMs() ?: break
            assertTrue(delay > 0)
            elapsed += delay
            attempts++
            assertTrue("No unbounded reconnect loop", attempts <= 4)
        }
        assertTrue(attempts > 0)
        assertTrue(elapsed <= 30_000)
        repeat(5) { assertNull(recovery.nextDelayMs()) }
        recovery.reset()
        assertNotNull(recovery.nextDelayMs())
    }

    @Test fun pausedPlaybackDoesNotReconnectOrConsumeItsRecoveryBudget() {
        val recovery = AutomaticPlayback.Recovery()
        repeat(8) { assertNull(recovery.nextDelayMs(playRequested = false)) }
        assertNotNull(recovery.nextDelayMs(playRequested = true))
    }
}
