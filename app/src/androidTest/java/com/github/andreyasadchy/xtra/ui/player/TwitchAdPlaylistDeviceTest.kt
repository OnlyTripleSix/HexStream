package com.github.andreyasadchy.xtra.ui.player

import android.net.Uri
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class TwitchAdPlaylistDeviceTest {
    private fun playlist(marker: String) = HlsPlaylistParser().parse(Uri.parse("https://example.test/live.m3u8"), """
        #EXTM3U
        #EXT-X-TARGETDURATION:6
        #EXT-X-MEDIA-SEQUENCE:100
        #EXT-X-PROGRAM-DATE-TIME:2026-01-01T00:00:00Z
        $marker
        #EXTINF:6,live
        segment.ts
    """.trimIndent().byteInputStream()) as HlsMediaPlaylist

    @Test fun retainedTwitchDateRangeIsDetectedWhenAppleInterstitialListIsEmpty() {
        assertEquals("com.github.andreyasadchy.xtra.qa", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
        val parsed = playlist("#EXT-X-DATERANGE:ID=\"stitched-ad-fixture\",CLASS=\"twitch-stitched-ad\",START-DATE=\"2026-01-01T00:00:00Z\",DURATION=30")
        assertTrue("This is the real Media3 parsing gap", parsed.interstitials.isEmpty())
        assertTrue(TwitchAdMarkers.containsAd(parsed.tags, parsed.segments.last().title, parsed.startTimeUs + parsed.segments.last().relativeStartTimeUs))
    }

    @Test fun unrelatedProgramDateRangeDoesNotBecomeAnAd() {
        val parsed = playlist("#EXT-X-DATERANGE:ID=\"program\",CLASS=\"game-round\",START-DATE=\"2026-01-01T00:00:00Z\",DURATION=30")
        assertFalse(TwitchAdMarkers.containsAd(parsed.tags, parsed.segments.last().title, parsed.startTimeUs))
    }
}
