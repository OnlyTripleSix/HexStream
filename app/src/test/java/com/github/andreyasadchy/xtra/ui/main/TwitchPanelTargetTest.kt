package com.github.andreyasadchy.xtra.ui.main

import org.junit.Assert.*
import org.junit.Test

class TwitchPanelTargetTest {
    @Test fun pollManagerStaysBoundToSelectedChannel() {
        val target = TwitchPanelTarget("Fixture", false)
        assertEquals("https://www.twitch.tv/popout/fixture/poll", target.url)
        assertTrue(target.allowsNavigation(target.url + "?ref=fixture"))
        assertFalse(target.allowsNavigation("https://www.twitch.tv/popout/other/poll"))
        assertFalse(target.allowsNavigation("https://www.twitch.tv/moderator/other"))
    }
    @Test fun loginNavigationDoesNotPermitUntrustedHostsOrSchemes() {
        val target = TwitchPanelTarget("fixture", false)
        assertTrue(target.allowsNavigation("https://www.twitch.tv/login"))
        assertTrue(target.allowsNavigation("https://passport.twitch.tv/login"))
        for (url in listOf("https://twitch.tv.example.org/login", "http://www.twitch.tv/login", "file:///sdcard/file", "https://name:password@www.twitch.tv/login", "https://www.twitch.tv:444/login")) assertFalse(url, target.allowsNavigation(url))
    }
    @Test fun fullControlsOnlyAllowTheirBoundChannelAndChildren() {
        val target = TwitchPanelTarget("fixture", true)
        assertTrue(target.allowsNavigation("https://www.twitch.tv/moderator/fixture"))
        assertTrue(target.allowsNavigation("https://www.twitch.tv/moderator/fixture/automod"))
        assertFalse(target.allowsNavigation("https://www.twitch.tv/moderator/fixture_other"))
    }
}
