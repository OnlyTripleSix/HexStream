package com.github.andreyasadchy.xtra.util

import com.github.andreyasadchy.xtra.model.ui.DownloadProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Keep the latest update for each download while its screen is stopped. No view access here. */
class DownloadProgressUpdates {
    private val pending = MutableStateFlow<Map<Int, DownloadProgress>>(emptyMap())
    val state = pending.asStateFlow()

    fun post(progress: DownloadProgress) {
        // Services reuse mutable progress objects. UI batches must own a stable copy.
        val snapshot = DownloadProgress(progress.id, progress.progress, progress.maxProgress, progress.bytes,
            progress.chatProgress, progress.maxChatProgress, progress.chatBytes, progress.chatOffsetSeconds,
            progress.lastSegmentUrl, progress.liveCommentsArrayStarted, progress.isLive, progress.lastSaved)
        pending.update { it + (snapshot.id to snapshot) }
    }

    fun acknowledge(batch: Map<Int, DownloadProgress>) {
        // A callback may have posted a newer update while this batch was rendered.
        pending.update { current -> current.filter { (id, progress) -> batch[id] !== progress } }
    }
}
