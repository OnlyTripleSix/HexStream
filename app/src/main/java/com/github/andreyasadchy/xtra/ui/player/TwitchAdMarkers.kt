package com.github.andreyasadchy.xtra.ui.player

import kotlin.time.Instant

/** Reads Twitch's retained HLS tags; Media3's interstitial list only accepts Apple's class. */
internal object TwitchAdMarkers {
    private val attribute = Regex("(?:^|,)([A-Z0-9-]+)=(?:\"([^\"]*)\"|([^,]*))")
    private val titles = setOf("Amazon", "Adform", "DCM")

    fun containsAd(tags: List<String>, segmentTitle: String?, segmentUnixUs: Long?): Boolean {
        if (segmentTitle?.substringBefore('|')?.trim() in titles) return true
        if (segmentUnixUs == null) return false
        val ranges = linkedMapOf<String, MutableMap<String, String>>()
        for (tag in tags) if (tag.startsWith("#EXT-X-DATERANGE:")) {
            val attrs = attribute.findAll(tag.substringAfter(':')).associate { it.groupValues[1] to
                (it.groups[2]?.value ?: it.groupValues[3]) }
            val id = attrs["ID"] ?: continue
            ranges.getOrPut(id) { linkedMapOf() }.putAll(attrs)
        }
        return ranges.any { (id, attrs) ->
            val ad = id.startsWith("stitched-ad-") || attrs["CLASS"] == "twitch-stitched-ad" || attrs.keys.any { it.startsWith("X-TV-TWITCH-AD-") }
            val start = dateUs(attrs["START-DATE"])
            if (!ad || start == null || segmentUnixUs < start) false
            else {
                val duration = (attrs["DURATION"] ?: attrs["PLANNED-DURATION"])?.toDoubleOrNull()
                    ?.takeIf { it.isFinite() && it > 0 && it <= 86400 }?.let { (it * 1_000_000).toLong() }
                val end = dateUs(attrs["END-DATE"]) ?: duration?.let { start + it }
                end == null || segmentUnixUs < end
            }
        }
    }

    private fun dateUs(value: String?): Long? = try {
        value?.let { Instant.parse(it) }?.let { Math.addExact(Math.multiplyExact(it.epochSeconds, 1_000_000), it.nanosecondsOfSecond / 1000L) }
    } catch (_: Exception) { null }
}
