package com.github.andreyasadchy.xtra.util

import android.content.Context
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.repository.ActionFailure
import com.github.andreyasadchy.xtra.repository.ChannelActionsApi

object ChannelActionSession {
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
