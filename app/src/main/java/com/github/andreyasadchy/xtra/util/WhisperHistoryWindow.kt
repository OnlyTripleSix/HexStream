package com.github.andreyasadchy.xtra.util

/** Cached old messages must not make a fresh server page appear to cover an un-fetched gap. */
internal object WhisperHistoryWindow {
    data class Refreshed(val messages: List<WhisperEntry>, val replaced: Boolean, val oldestServerTime: Long)
    fun newest(owner: String, current: List<WhisperEntry>, incoming: List<WhisperEntry>, duringRead: List<WhisperEntry>,
               hasServerWindow: Boolean, previousOldest: Long, deleted: Set<String>): Refreshed {
        val overlap = hasServerWindow && incoming.any { candidate -> current.any { it.id == candidate.id } }
        val oldest = incoming.minOfOrNull { it.time } ?: Long.MAX_VALUE
        val base = if (overlap) current + duringRead else current.filter { it.id.startsWith("local-") && it.time >= oldest } + duringRead
        return Refreshed(WhisperMessages.merge(owner, base, incoming, deleted), !overlap, if (overlap) previousOldest else oldest)
    }
}
