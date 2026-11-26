package com.github.andreyasadchy.xtra.ui.main

import org.junit.Assert.*
import org.junit.Test

class UpdateInstallSessionsTest {
    @Test fun cleanupKeepsOtherTargetsUnknownDatesAndAttemptsNewerThanTheInstalledApp() {
        val own = "example.fixture"
        assertTrue(UpdateInstallSessions.superseded(own, own, 100, 200))
        assertFalse(UpdateInstallSessions.superseded("other.fixture", own, 100, 200))
        assertFalse(UpdateInstallSessions.superseded(null, own, 100, 200))
        assertFalse(UpdateInstallSessions.superseded(own, own, 0, 200))
        assertFalse(UpdateInstallSessions.superseded(own, own, -1, 200))
        assertFalse(UpdateInstallSessions.superseded(own, own, 200, 200))
        assertFalse(UpdateInstallSessions.superseded(own, own, 300, 200))
    }
}
