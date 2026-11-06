package com.github.andreyasadchy.xtra.util

import android.content.Context
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.repository.ActionFailure
import com.github.andreyasadchy.xtra.repository.WhisperHistoryApi
import org.json.JSONObject

object WhisperHistorySession {
    const val VERIFIED_HEADERS = "hex_whisper_history_headers"
    const val VERIFIED_UNTIL = "hex_whisper_history_verified_until"
    internal fun baseHeaders(context: Context): Map<String, String> {
        val configured = WhisperHistoryHeaders.normalized(TwitchApiHelper.getGQLHeaders(context, true))
        val webToken = context.tokenPrefs().getString(C.GQL_TOKEN_WEB, null)
        if (!webToken.isNullOrBlank()) {
            val authorization = TwitchApiHelper.addTokenPrefixGQL(webToken)
            val client = context.prefs().getString(C.GQL_CLIENT_ID_WEB, "kimne78kx3ncx6brgo4mv6wki5h1ko").orEmpty()
            if (configured[C.HEADER_TOKEN] == authorization && configured[C.HEADER_CLIENT_ID] == client) return configured.toMap()
            return mapOf(C.HEADER_CLIENT_ID to client, C.HEADER_TOKEN to authorization)
        }
        return configured.toMap()
    }
    internal fun headers(context: Context): Map<String, String> {
        val verified = runCatching {
            val json = JSONObject(context.tokenPrefs().getString(VERIFIED_HEADERS, null).orEmpty())
            json.keys().asSequence().associateWith { json.optString(it) }
        }.getOrDefault(emptyMap())
        return WhisperHistoryHeaders.choose(baseHeaders(context), verified, System.currentTimeMillis() < context.tokenPrefs().getLong(VERIFIED_UNTIL, 0))
    }
    fun open(context: Context): WhisperHistoryApi {
        val app = context.applicationContext
        val id = app.tokenPrefs().getString(C.USER_ID, null)
        val sessionHeaders = headers(app)
        if (id.isNullOrBlank() || sessionHeaders[C.HEADER_TOKEN].isNullOrBlank()) throw ActionFailure(WhisperHistoryApi.LOGIN_REQUIRED)
        return WhisperHistoryApi((app as XtraApp).xtraModule.okHttpClient.value, id, sessionHeaders) {
            app.tokenPrefs().getString(C.USER_ID, null) == id && headers(app) == sessionHeaders
        }
    }
}
