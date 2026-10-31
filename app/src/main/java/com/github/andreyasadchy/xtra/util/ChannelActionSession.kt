package com.github.andreyasadchy.xtra.util

import android.content.Context
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.repository.ActionFailure
import com.github.andreyasadchy.xtra.repository.ChannelActionsApi
import kotlinx.coroutines.CancellationException

object ChannelActionSession {
    suspend fun canModerate(context: Context, channelId: String?): Boolean {
        if (channelId.isNullOrBlank()) return false
        return try { open(context).canModerate(channelId) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
    }
    fun open(context: Context): ChannelActionsApi {
        val app = context.applicationContext
        val id = app.tokenPrefs().getString(C.USER_ID, null)
        val headers = TwitchApiHelper.getHelixHeaders(app).toMap()
        if (id.isNullOrBlank() || headers[C.HEADER_TOKEN].isNullOrBlank())
            throw ActionFailure("Connect your active account again. In Settings → API token settings → Get token for selected APIs, select Both or Helix, then log in and grant the requested permissions.")
        return ChannelActionsApi((app as XtraApp).xtraModule.okHttpClient.value, id, headers) {
            app.tokenPrefs().getString(C.USER_ID, null) == id && TwitchApiHelper.getHelixHeaders(app) == headers
        }
    }
}
