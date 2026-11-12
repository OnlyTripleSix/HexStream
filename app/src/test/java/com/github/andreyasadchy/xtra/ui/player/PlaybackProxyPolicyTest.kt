package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackProxyPolicyTest {
    @Test fun enabledListIsConsumedInOrderAndNeverSilentlyGoesDirect() {
        for (index in 0..5) assertEquals(PlaybackProxyPolicy.Route.PROXY, PlaybackProxyPolicy.route(true, index, 6, false))
        assertEquals(PlaybackProxyPolicy.Route.EXHAUSTED, PlaybackProxyPolicy.route(true, 6, 6, false))
        assertEquals(PlaybackProxyPolicy.Route.EXHAUSTED, PlaybackProxyPolicy.route(true, 1000, 6, false))
    }
    @Test fun emptyOrInvalidSelectionRequiresExplicitFallback() {
        assertEquals(PlaybackProxyPolicy.Route.EXHAUSTED, PlaybackProxyPolicy.route(true, 0, 0, false))
        assertEquals(PlaybackProxyPolicy.Route.EXHAUSTED, PlaybackProxyPolicy.route(true, -1, 6, false))
    }
    @Test fun directChoiceAndOptInFallbackRemainAvailable() {
        assertEquals(PlaybackProxyPolicy.Route.DIRECT, PlaybackProxyPolicy.route(false, 0, 6, false))
        assertEquals(PlaybackProxyPolicy.Route.DIRECT, PlaybackProxyPolicy.route(true, 6, 6, true))
        assertEquals(PlaybackProxyPolicy.Route.PROXY, PlaybackProxyPolicy.route(true, 1, 6, true))
    }
    @Test fun retryStartsANewBoundedPassWithoutChangingPresetOrder() {
        assertEquals(PlaybackProxyPolicy.Route.EXHAUSTED, PlaybackProxyPolicy.route(true, 6, 6, false))
        assertEquals(PlaybackProxyPolicy.Route.PROXY, PlaybackProxyPolicy.route(true, 0, 6, false))
    }
    @Test fun onlyAnOptedInProxyToDirectTransitionInvalidatesSavedProxySources() {
        var cached: String? = "https://proxy.example.test/live/fixture"
        val invalidate = { cached = null }
        assertEquals(PlaybackProxyPolicy.Route.DIRECT, PlaybackProxyPolicy.route(false, 0, 1, false, invalidate))
        assertNotNull(cached)
        assertEquals(PlaybackProxyPolicy.Route.PROXY, PlaybackProxyPolicy.route(true, 0, 1, true, invalidate))
        assertNotNull(cached)
        assertEquals(PlaybackProxyPolicy.Route.EXHAUSTED, PlaybackProxyPolicy.route(true, 1, 1, false, invalidate))
        assertNotNull(cached)
        assertEquals(PlaybackProxyPolicy.Route.DIRECT, PlaybackProxyPolicy.route(true, 1, 1, true, invalidate))
        assertNull(cached)
    }
}
