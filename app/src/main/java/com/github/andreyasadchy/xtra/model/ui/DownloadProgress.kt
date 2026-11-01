package com.github.andreyasadchy.xtra.model.ui

class DownloadProgress(
    val id: Int,
    var progress: Int = 0,
    var maxProgress: Int = 100,
    var bytes: Long = 0,
    var chatProgress: Int = 0,
    var maxChatProgress: Int = 100,
    var chatBytes: Long = 0,
    var chatOffsetSeconds: Int = 0,
    var lastSegmentUrl: String? = null,
    var liveCommentsArrayStarted: Boolean = false,
    var isLive: Boolean = false,
    var lastSaved: Long = 0,
) {
    /** A resume bookmark describes the last completed segment, never the full download history. */
    fun recordMediaSegment(byteCount: Long, url: String?) {
        bytes += byteCount
        lastSegmentUrl = url
    }
}
