package com.github.andreyasadchy.xtra.repository

/** Membership is checked by immutable broadcaster ID, never a name or a UI toggle. */
internal object ModeratorPermission {
    data class Page(val channelIds: Set<String>, val next: String?)
    suspend fun check(accountId: String, channelId: String, fetch: suspend (String?) -> Page): Boolean {
        if (!accountId.matches(Regex("[0-9]+")) || !channelId.matches(Regex("[0-9]+"))) return false
        if (accountId == channelId) return true
        val visited = mutableSetOf<String?>()
        var cursor: String? = null
        repeat(100) {
            if (!visited.add(cursor)) throw ActionFailure("Twitch could not finish verifying your moderator role. Reopen the channel tools.")
            val page = fetch(cursor)
            if (channelId in page.channelIds) return true
            cursor = page.next?.takeIf { it.isNotBlank() } ?: return false
        }
        throw ActionFailure("Twitch could not finish verifying your moderator role. Reopen the channel tools.")
    }
}
