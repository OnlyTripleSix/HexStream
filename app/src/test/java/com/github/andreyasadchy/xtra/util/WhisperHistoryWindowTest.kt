package com.github.andreyasadchy.xtra.util

import org.junit.Assert.*
import org.junit.Test

class WhisperHistoryWindowTest {
    private fun row(id: String, time: Long) = WhisperEntry(id, "22", "Peer", "11", "Owner", "Message", time)
    @Test fun oldCacheCannotHideAnUnfetchedGapBehindTheNewestPage() {
        val result = WhisperHistoryWindow.newest("11", listOf(row("cached-old", 1)), listOf(row("recent", 100)), emptyList(), false, Long.MAX_VALUE, emptySet())
        assertEquals(listOf("recent"), result.messages.map { it.id }); assertTrue(result.replaced); assertEquals(100L, result.oldestServerTime)
    }
    @Test fun overlappingRefreshKeepsTheLoadedOlderWindowAndFrontier() {
        val result = WhisperHistoryWindow.newest("11", listOf(row("old", 1), row("shared", 100)), listOf(row("shared", 100), row("new", 200)), emptyList(), true, 1, emptySet())
        assertEquals(listOf("old", "shared", "new"), result.messages.map { it.id }); assertFalse(result.replaced); assertEquals(1L, result.oldestServerTime)
    }
    @Test fun aLongOfflineGapStartsAContiguousNewestWindow() {
        val result = WhisperHistoryWindow.newest("11", listOf(row("old", 1)), listOf(row("recent", 100)), emptyList(), true, 1, emptySet())
        assertTrue(result.replaced); assertEquals(listOf("recent"), result.messages.map { it.id })
    }
    @Test fun messagesArrivingDuringTheReadAreRetainedEvenWithClockSkew() {
        val result = WhisperHistoryWindow.newest("11", emptyList(), listOf(row("server", 100)), listOf(row("live", 50)), false, Long.MAX_VALUE, emptySet())
        assertEquals(setOf("live", "server"), result.messages.map { it.id }.toSet())
    }
    @Test fun removedMessagesCannotReturnThroughTheCache() {
        val result = WhisperHistoryWindow.newest("11", listOf(row("gone", 50), row("shared", 100)), listOf(row("shared", 100)), emptyList(), true, 50, setOf("gone"))
        assertEquals(listOf("shared"), result.messages.map { it.id })
    }
}
