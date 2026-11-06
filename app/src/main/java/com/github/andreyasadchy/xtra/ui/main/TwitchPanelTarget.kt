package com.github.andreyasadchy.xtra.ui.main

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class TwitchPanelTarget(login: String, fullTools: Boolean) {
    private val channel = login.lowercase()
    private val path = if (fullTools) "/moderator/$channel" else "/popout/$channel/poll"
    val url = "https://www.twitch.tv$path"

    init { require(channel.matches(Regex("[a-z0-9_]{1,25}"))) }

    fun allowsNavigation(value: String): Boolean {
        val target = value.toHttpUrlOrNull() ?: return false
        if (target.scheme != "https" || target.port != 443 || target.username.isNotEmpty() || target.password.isNotEmpty()) return false
        if (target.host in setOf("id.twitch.tv", "passport.twitch.tv")) return true
        if (target.host !in setOf("www.twitch.tv", "twitch.tv", "m.twitch.tv")) return false
        val next = target.encodedPath.lowercase()
        return next == path || next.startsWith("$path/") || next == "/login" || next.startsWith("/passport/")
    }
}
