package com.github.andreyasadchy.xtra.ui.player

import kotlin.time.Instant
import org.junit.Assert.*
import org.junit.Test

class TwitchAdMarkersTest {
    private val start = "2026-01-01T00:00:00Z"
    private val time = Instant.parse(start).epochSeconds * 1_000_000
    private fun tag(attrs: String) = "#EXT-X-DATERANGE:ID=\"stitched-ad-fixture\",START-DATE=\"$start\",$attrs"

    @Test fun recognizesRetainedTwitchClassWithoutAppleInterstitial() {
        assertTrue(TwitchAdMarkers.containsAd(listOf(tag("CLASS=\"twitch-stitched-ad\",DURATION=30")), "live", time + 5_000_000))
    }
    @Test fun supportsAdIdAndTwitchClientAttributes() {
        assertTrue(TwitchAdMarkers.containsAd(listOf(tag("DURATION=30")), null, time))
        val tag = "#EXT-X-DATERANGE:ID=\"other\",START-DATE=\"$start\",X-TV-TWITCH-AD-ID=\"fixture\",DURATION=30"
        assertTrue(TwitchAdMarkers.containsAd(listOf(tag), "live", time))
    }
    @Test fun respectsRangeBoundariesAndExplicitEndBeforePlannedDuration() {
        val tags = listOf(tag("PLANNED-DURATION=60,END-DATE=\"2026-01-01T00:00:10Z\""))
        assertFalse(TwitchAdMarkers.containsAd(tags, null, time - 1))
        assertTrue(TwitchAdMarkers.containsAd(tags, null, time))
        assertFalse(TwitchAdMarkers.containsAd(tags, null, time + 10_000_000))
    }
    @Test fun combinesEndUpdatesByIdAndHandlesQuotedCommas() {
        val tags = listOf(tag("X-TV-TWITCH-AD-TITLE=\"One, two\",PLANNED-DURATION=60"),
            "#EXT-X-DATERANGE:ID=\"stitched-ad-fixture\",END-DATE=\"2026-01-01T00:00:05Z\"")
        assertTrue(TwitchAdMarkers.containsAd(tags, null, time + 4_000_000))
        assertFalse(TwitchAdMarkers.containsAd(tags, null, time + 5_000_000))
    }
    @Test fun ongoingMarkedBreakCanHaveNoEndYet() {
        assertTrue(TwitchAdMarkers.containsAd(listOf(tag("CLASS=\"twitch-stitched-ad\"")), null, time + 10_000_000))
    }
    @Test fun unrelatedOrMalformedTagsDoNotHidePrograms() {
        for (tag in listOf("#EXT-X-DATERANGE:ID=\"program\",START-DATE=\"$start\",DURATION=30", tag("DURATION=30").replace(start, "bad-date"),
                "#EXT-X-DATERANGE:ID=\"stitched-ad-fixture\",DURATION=30", "#EXTINF:6,ordinary")) {
            assertFalse(tag, TwitchAdMarkers.containsAd(listOf(tag), "live", time))
        }
        assertFalse(TwitchAdMarkers.containsAd(listOf(tag("DURATION=30")), null, null))
    }
    @Test fun legacyAdSegmentTitlesStillWorkWithoutProgramDateTime() {
        for (title in listOf("Amazon|fixture", "Adform", "DCM|fixture")) assertTrue(TwitchAdMarkers.containsAd(emptyList(), title, null))
        assertFalse(TwitchAdMarkers.containsAd(emptyList(), "Amazonian game", null))
    }
    @Test fun plannedDurationExpiresAndFreshUnmarkedManifestRecovers() {
        val tags = listOf(tag("PLANNED-DURATION=2.5"))
        assertTrue(TwitchAdMarkers.containsAd(tags, "live", time + 2_499_999))
        assertFalse(TwitchAdMarkers.containsAd(tags, "live", time + 2_500_000))
        assertFalse(TwitchAdMarkers.containsAd(emptyList(), "live", time + 1_000_000))
    }
}
