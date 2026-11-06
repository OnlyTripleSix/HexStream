package com.github.andreyasadchy.xtra.util

import android.content.Context
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.repository.ActionFailure
import com.github.andreyasadchy.xtra.repository.ChannelActionsApi
import com.github.andreyasadchy.xtra.repository.text
import com.github.andreyasadchy.xtra.repository.TwitchModeratorRole
import kotlinx.coroutines.CancellationException

object ChannelActionSession {
    data class ModeratorAccess(val channelId: String = "", val allowed: Boolean = false, val error: String? = null)
    private data class DisplayRole(val access: ModeratorAccess, val session: ChannelActionsApi, val expires: Long)
    private val displayRoles = java.util.concurrent.ConcurrentHashMap<String, DisplayRole>()

    suspend fun moderatorAccess(context: Context, channelId: String?, channelLogin: String?): ModeratorAccess {
        val key = channelId?.takeIf { it.isNotBlank() } ?: "login:${channelLogin.orEmpty().lowercase()}"
        val now = android.os.SystemClock.elapsedRealtime()
        displayRoles[key]?.let { cached ->
            if (now < cached.expires && runCatching { cached.session.checkCurrent() }.isSuccess) return cached.access
            displayRoles.remove(key, cached)
        }
        return try {
            val session = open(context)
            val resolvedId = channelId?.takeIf { it.isNotBlank() }
                ?: channelLogin?.takeIf { it.isNotBlank() }?.let { session.user(it).text("id") }
                ?: throw ActionFailure("Channel details are still loading. Reopen the controls in a moment.")
            ModeratorAccess(resolvedId, session.canModerate(resolvedId)).also { access ->
                // This short cache only controls visibility. Every write still verifies the live role.
                if (access.allowed) {
                    displayRoles.entries.removeIf { it.value.expires <= now }
                    if (displayRoles.size >= 32) displayRoles.clear()
                    displayRoles[key] = DisplayRole(access, session, now + 15_000)
                }
            }
        } catch (error: CancellationException) { throw error }
        catch (error: ActionFailure) { ModeratorAccess(error = error.message) }
        catch (_: Exception) { ModeratorAccess(error = "Twitch could not verify your moderator access. Check your connection and retry.") }
    }

    fun showModeratorSetup(context: Context, message: String) {
        context.getAlertDialogBuilder().setTitle("Moderator access needs attention")
            .setMessage("$message\n\nIn API token settings, choose Both under Get token for selected APIs, then reconnect the account you use to moderate. Return to the channel to retry.")
            .setNegativeButton("Close", null)
            .setPositiveButton("Open settings") { _, _ ->
                context.startActivity(android.content.Intent(context, com.github.andreyasadchy.xtra.ui.settings.SettingsActivity::class.java))
            }.show()
    }
    fun open(context: Context): ChannelActionsApi {
        val app = context.applicationContext
        val id = app.tokenPrefs().getString(C.USER_ID, null)
        val headers = TwitchApiHelper.getHelixHeaders(app).toMap()
        if (id.isNullOrBlank() || headers[C.HEADER_TOKEN].isNullOrBlank())
            throw ActionFailure("Connect your active account again. In Settings → API token settings → Get token for selected APIs, select Both or Helix, then log in and grant the requested permissions.")
        val client = (app as XtraApp).xtraModule.okHttpClient.value
        val webHeaders = WhisperHistorySession.headers(app)
        val webRole = webHeaders.takeIf { !it[C.HEADER_TOKEN].isNullOrBlank() }?.let {
            TwitchModeratorRole(client, id, it) {
                app.tokenPrefs().getString(C.USER_ID, null) == id && WhisperHistorySession.headers(app) == webHeaders
            }
        }
        return ChannelActionsApi(client, id, headers, roleFallback = webRole?.let { role -> { channel -> role.check(channel) } }) {
            app.tokenPrefs().getString(C.USER_ID, null) == id && TwitchApiHelper.getHelixHeaders(app) == headers
        }
    }
}
