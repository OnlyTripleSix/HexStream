package com.github.andreyasadchy.xtra.util

import com.github.andreyasadchy.xtra.model.ui.DownloadProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DownloadProgressUpdatesTest {
    @Test fun stoppedScreenReceivesLatestProgressForEveryActiveDownload() = runBlocking {
        val updates = DownloadProgressUpdates()
        updates.post(DownloadProgress(1, progress = 10))
        updates.post(DownloadProgress(2, progress = 20))
        updates.post(DownloadProgress(1, progress = 30))
        val resumed = updates.state.first()
        assertEquals(setOf(1, 2), resumed.keys)
        assertEquals(30, resumed.getValue(1).progress)
        assertEquals(20, resumed.getValue(2).progress)
    }
    @Test fun reusedServiceObjectCannotMutateAnAlreadyPublishedBatch() {
        val updates = DownloadProgressUpdates()
        val source = DownloadProgress(1, 10, 120, 300, 4, 100, 50, 6, "segment-a", true, true, 123)
        updates.post(source)
        val published = updates.state.value.getValue(1)
        source.progress = 11; source.bytes = 400; source.lastSegmentUrl = "segment-b"
        assertEquals(10, published.progress); assertEquals(300L, published.bytes); assertEquals("segment-a", published.lastSegmentUrl)
        assertEquals(120, published.maxProgress); assertEquals(4, published.chatProgress)
        assertEquals(100, published.maxChatProgress); assertEquals(50L, published.chatBytes)
        assertEquals(6, published.chatOffsetSeconds); assertTrue(published.liveCommentsArrayStarted)
        assertTrue(published.isLive); assertEquals(123L, published.lastSaved)
        updates.post(source)
        assertEquals(11, updates.state.value.getValue(1).progress)
    }
    @Test fun renderingAnOlderBatchCannotEraseANewerCallbackForTheSameDownload() {
        val updates = DownloadProgressUpdates()
        val source = DownloadProgress(1, progress = 10)
        updates.post(source); val rendered = updates.state.value
        source.progress = 20; updates.post(source)
        updates.acknowledge(rendered)
        assertEquals(20, updates.state.value.getValue(1).progress)
    }
    @Test fun acknowledgingRenderedItemsKeepsAnUnrelatedNewDownload() {
        val updates = DownloadProgressUpdates()
        updates.post(DownloadProgress(1)); val rendered = updates.state.value
        updates.post(DownloadProgress(2))
        updates.acknowledge(rendered)
        assertEquals(setOf(2), updates.state.value.keys)
        updates.acknowledge(updates.state.value)
        assertTrue(updates.state.value.isEmpty())
    }
    @Test fun manyCompletedSegmentsKeepOneResumeUrlAndCorrectByteTotal() {
        val progress = DownloadProgress(9, bytes = 5, lastSegmentUrl = "legacy-concatenated-url")
        repeat(10_000) { progress.recordMediaSegment(100, "https://media.invalid/segment-$it.ts") }
        assertEquals("https://media.invalid/segment-9999.ts", progress.lastSegmentUrl)
        assertEquals(1_000_005L, progress.bytes)
        assertEquals(0L, progress.chatBytes)
    }
    @Test fun firstCompletedSegmentDoesNotPrefixNullOrKeepAnOldBookmark() {
        val progress = DownloadProgress(9)
        progress.recordMediaSegment(10, "first.ts")
        progress.recordMediaSegment(20, "second.ts")
        assertEquals("second.ts", progress.lastSegmentUrl)
        assertEquals(30L, progress.bytes)
    }
}
