package com.github.andreyasadchy.xtra.ui.player

internal object PlaybackProxyPolicy {
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
