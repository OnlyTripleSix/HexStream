package com.github.andreyasadchy.xtra.util

import com.github.andreyasadchy.xtra.model.ui.CustomProxy
import org.junit.Assert.*
import org.junit.Test

class PlaybackProxyPresetsTest {
    @Test fun defaultsHaveRequiredPreferenceOrderAndExactUrls() {
        val items = PlaybackProxyPresets.initialItems()
        assertEquals(listOf("eu2.luminous.dev", "eu.luminous.dev", "lb-as.cdn-perfprod.com"), PlaybackProxyPresets.hosts)
        items.forEachIndexed { index, item ->
            assertEquals("https://${PlaybackProxyPresets.hosts[index]}/live/\$channel?allow_source=true&allow_audio_only=true&fast_bread=true", item.url)
            assertEquals(index, item.position)
            assertTrue(item.enabled)
            assertFalse(item.addQueryParams)
        }
    }
    @Test fun importPreservesCustomUrlAndDisabledStateWithoutDuplicateIds() {
        val legacy = CustomProxy("https://example.test/live/\$channel?custom=yes", true, 10, false).apply { id = 99 }
        val result = PlaybackProxyPresets.imports(PlaybackProxyPresets.initialItems(), listOf(legacy, legacy))
        assertEquals(1, result.size)
        assertEquals(legacy.url, result.single().url)
        assertEquals(PlaybackProxyPresets.urls.size, result.single().position)
        assertFalse(result.single().enabled)
        assertTrue(result.single().addQueryParams)
        assertEquals(0, result.single().id)
    }
    @Test fun importDoesNotOverwriteGlobalChoiceOrOrder() {
        val existing = CustomProxy("https://custom.test", false, 20, false)
        assertTrue(PlaybackProxyPresets.imports(listOf(existing), listOf(CustomProxy(existing.url, true, 0, true))).isEmpty())
        assertFalse(existing.enabled)
        assertEquals(20, existing.position)
    }
}
