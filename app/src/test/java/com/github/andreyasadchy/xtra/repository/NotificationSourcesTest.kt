package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.model.ui.Stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NotificationSourcesTest {
    @Test fun independentChannelAlertSurvivesFailedFollowingLookup() = runBlocking {
        val result = NotificationSources.collect(listOf({ listOf(Stream(channelId = "22")) }, { error("Following unavailable") }))
        assertEquals(listOf("22"), result.streams.map { it.channelId }); assertFalse(result.complete)
    }
    @Test fun failedIndependentLookupStillAllowsFollowingAlerts() = runBlocking {
        val result = NotificationSources.collect(listOf({ error("Unavailable") }, { listOf(Stream(channelId = "33")) }))
        assertEquals(listOf("33"), result.streams.map { it.channelId }); assertFalse(result.complete)
    }
    @Test fun sameChannelFromBothSourcesOnlyAlertsOnce() = runBlocking {
        val result = NotificationSources.collect(listOf({ listOf(Stream(channelId = "22", id = "old")) }, { listOf(Stream(channelId = "22", id = "new")) }))
        assertEquals(1, result.streams.size); assertEquals("old", result.streams.single().id); assertTrue(result.complete)
    }
    @Test fun successfulEmptySourcesCanConfirmOfflineButFailedOnesCannot() = runBlocking {
        assertTrue(NotificationSources.collect(listOf({ emptyList() })).complete)
        assertFalse(NotificationSources.collect(listOf({ error("Unavailable") })).complete)
    }
    @Test fun accountSwitchCancellationIsNeverConvertedIntoPartialSuccess() {
        assertThrows(CancellationException::class.java) { runBlocking { NotificationSources.collect(listOf({ throw CancellationException() }, { listOf(Stream(channelId = "22")) })) } }
    }
}
