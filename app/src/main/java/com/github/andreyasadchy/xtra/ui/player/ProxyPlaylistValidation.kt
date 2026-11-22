package com.github.andreyasadchy.xtra.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException

@OptIn(UnstableApi::class)
internal class ProxyPlaylistRejected(val adMarked: Boolean) : IOException() {
    var rejectedMaster: HlsMultivariantPlaylist? = null
    val reason get() = if (adMarked) "ad_marked" else "not_playable_live"
}

@OptIn(UnstableApi::class)
internal fun validateProxyPlaylist(playlist: HlsMediaPlaylist) {
    if (checkPlaylist(playlist)) throw ProxyPlaylistRejected(adMarked = true)
    if (playlist.hasEndTag || (playlist.segments.none { !it.hasGapTag } &&
            playlist.trailingParts.none { !it.isPreload && !it.hasGapTag })) {
        throw ProxyPlaylistRejected(adMarked = false)
    }
}

@OptIn(UnstableApi::class)
internal class ProxyPlaylistLoadPolicy : DefaultLoadErrorHandlingPolicy(1) {
    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        if (info.exception is ProxyPlaylistRejected || info.errorCount > 1) C.TIME_UNSET
        else super.getRetryDelayMsFor(info)

    override fun getFallbackSelectionFor(
        options: LoadErrorHandlingPolicy.FallbackOptions,
        info: LoadErrorHandlingPolicy.LoadErrorInfo,
    ): LoadErrorHandlingPolicy.FallbackSelection? =
        if (info.exception is ProxyPlaylistRejected) null else super.getFallbackSelectionFor(options, info)
}

@OptIn(UnstableApi::class)
internal fun checkPlaylist(playlist: HlsMediaPlaylist?): Boolean {
    if (playlist == null) return false
    val tail = playlist.trailingParts.lastOrNull() ?: playlist.segments.lastOrNull()
    val time = tail?.let { if (playlist.hasProgramDateTime) playlist.startTimeUs + it.relativeStartTimeUs else null }
    val title = playlist.segments.lastOrNull()?.title.takeIf { playlist.trailingParts.isEmpty() }
    if (TwitchAdMarkers.containsAd(playlist.tags, title, time)) return true
    return time?.let { segmentStartTime ->
        playlist.interstitials.any { dateRange ->
            if (dateRange.id.startsWith("stitched-ad-")
                || dateRange.clientDefinedAttributes.find { it.name == "CLASS" }?.textValue == "twitch-stitched-ad"
                || dateRange.clientDefinedAttributes.find { it.name.startsWith("X-TV-TWITCH-AD-") } != null
            ) {
                val startTime = dateRange.startDateUnixUs
                val endTime = dateRange.endDateUnixUs.takeIf { it != androidx.media3.common.C.TIME_UNSET }
                    ?: dateRange.durationUs.takeIf { it != androidx.media3.common.C.TIME_UNSET }?.let { startTime + it }
                    ?: dateRange.plannedDurationUs.takeIf { it != androidx.media3.common.C.TIME_UNSET }?.let { startTime + it }
                if (endTime != null) {
                    segmentStartTime in startTime..<endTime
                } else false
            } else false
        }
    } == true
}
