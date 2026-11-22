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

    @Test fun proxyParserRejectsMarkedMediaBeforePublishingIt() {
        val parser = ExoPlayerService.CustomHlsPlaylistParserFactory(::validateProxyPlaylist)
            .createPlaylistParser()
        val uri = Uri.parse("https://example.test/live.m3u8")
        val media = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:100
            #EXT-X-PROGRAM-DATE-TIME:2026-01-01T00:00:00Z
            #EXTINF:6,live
            segment.ts
        """.trimIndent()
        val marked = media.replace("#EXTINF", """
            #EXT-X-DATERANGE:ID="stitched-ad-fixture",CLASS="twitch-stitched-ad",START-DATE="2026-01-01T00:00:00Z",DURATION=30
            #EXTINF
        """.trimIndent())
        val failure = runCatching { parser.parse(uri, marked.byteInputStream()) }.exceptionOrNull()
        assertTrue((failure as ProxyPlaylistRejected).adMarked)
        val afterBreak = marked.replaceFirst("2026-01-01T00:00:00Z", "2026-01-01T00:00:31Z")
        assertTrue(parser.parse(uri, afterBreak.byteInputStream()) is HlsMediaPlaylist)
        val ended = runCatching {
            parser.parse(uri, (media + "\n#EXT-X-ENDLIST").byteInputStream())
        }.exceptionOrNull()
        assertFalse((ended as ProxyPlaylistRejected).adMarked)
    }

    @Test fun allMarkedCandidatesExhaustThePass() {
        val seen = mutableListOf<Int>()
        assertNull(firstCleanCandidate(listOf(true, true, true), seen))
        assertEquals(listOf(0, 1, 2), seen)
    }

    @Test fun markedAndFailedCandidatesAreSkippedUntilMediaIsClean() {
        val seen = mutableListOf<Int>()
        assertEquals(3, firstCleanCandidate(listOf(true, null, true, false, false), seen))
        assertEquals(listOf(0, 1, 2, 3), seen)
    }

    private fun firstCleanCandidate(candidates: List<Boolean?>, seen: MutableList<Int>): Int? {
        val uri = Uri.parse("https://example.test/live.m3u8")
        val parser = ExoPlayerService.CustomHlsPlaylistParserFactory(::validateProxyPlaylist)
            .createPlaylistParser()
        for (index in 0..candidates.size) {
            if (PlaybackProxyPolicy.route(true, index, candidates.size, false) !=
                PlaybackProxyPolicy.Route.PROXY) return null
            seen.add(index)
            val marked = candidates[index] ?: continue // Fixture transport failure.
            val media = """
                #EXTM3U
                #EXT-X-TARGETDURATION:6
                #EXTINF:6,${if (marked) "Amazon" else "live"}
                segment.ts
            """.trimIndent()
            try {
                parser.parse(uri, media.byteInputStream())
                return index
            } catch (error: ProxyPlaylistRejected) {
                assertTrue(error.adMarked)
            }
        }
        return null
    }
}
