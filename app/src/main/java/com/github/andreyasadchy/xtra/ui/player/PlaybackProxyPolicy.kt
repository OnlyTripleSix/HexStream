package com.github.andreyasadchy.xtra.ui.player

internal object PlaybackProxyPolicy {
    class AdRecovery {
        private var delaysUsed = 0
        private var sawAd = false

        fun rejected(hasAd: Boolean, stablePlayback: Boolean) {
            if (stablePlayback) reset()
            sawAd = sawAd || hasAd
        }

        fun nextDelayMs(): Long? = if (sawAd && delaysUsed < adRetryDelays.size) {
            adRetryDelays[delaysUsed++]
        } else null

        fun reset() {
            delaysUsed = 0
            sawAd = false
        }
    }

    private val adRetryDelays = longArrayOf(15_000, 25_000, 30_000)

    enum class Route { PROXY, DIRECT, EXHAUSTED }
    fun route(requested: Boolean, index: Int, count: Int, allowDirect: Boolean, invalidatePlaylist: () -> Unit = {}): Route {
        val route = when {
            !requested -> Route.DIRECT
            index in 0 until count -> Route.PROXY
            allowDirect -> Route.DIRECT
            else -> Route.EXHAUSTED
        }
        // A permitted route change must not reuse a saved URL/quality list from the old proxy.
        if (requested && route == Route.DIRECT) invalidatePlaylist()
        return route
    }
}
