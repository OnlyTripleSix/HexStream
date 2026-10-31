package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.model.ui.Stream
import kotlinx.coroutines.CancellationException

internal object NotificationSources {
    data class Result(val streams: List<Stream>, val complete: Boolean)
    suspend fun collect(sources: List<suspend () -> List<Stream>>): Result {
        val streams = linkedMapOf<String, Stream>()
        var complete = true
        sources.forEach { source ->
            try { source().forEach { stream -> stream.channelId?.takeIf { it.isNotBlank() }?.let { streams.putIfAbsent(it, stream) } } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { complete = false }
        }
        return Result(streams.values.toList(), complete)
    }
}
