package com.github.andreyasadchy.xtra.repository

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import okio.BufferedSink
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Account-bound requests. Never retry a mutation or pass private responses to network logs. */
class ChannelActionsApi(client: OkHttpClient, val userId: String, private val headers: Map<String, String>, private val current: () -> Boolean) {
    val client: OkHttpClient = client.newBuilder().apply {
        interceptors().clear()
        networkInterceptors().clear()
        retryOnConnectionFailure(false)
        followRedirects(false)
        followSslRedirects(false)
    }.build()
    internal var callFactory: Call.Factory = this.client

    private suspend fun request(path: String, method: String = "GET", query: Map<String, String> = emptyMap(), body: JsonObject? = null, moderatorChannel: String? = null): JsonObject {
        checkCurrent()
        if (method != "GET" && (path.startsWith("moderation/") || path in setOf("chat/settings", "chat/announcements"))) {
            requireModerator(moderatorChannel ?: query["broadcaster_id"].orEmpty())
            checkCurrent()
        }
        val url = "https://api.twitch.tv/helix/$path".toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val req = try { Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
            val payload = when (method) {
                "POST", "PUT", "PATCH" -> (body ?: buildJsonObject {}).toString().toRequestBody("application/json".toMediaType())
                "DELETE" -> "".toRequestBody()
                else -> null
            }
            method(method, payload?.let(::SingleSubmissionBody))
        }.build() } catch (_: IllegalArgumentException) {
            throw ActionFailure("Saved Twitch login cannot be used. Reconnect the active account.")
        }
        val response = suspendCancellableCoroutine { continuation ->
            val call = callFactory.newCall(req)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(ActionFailure("Connection interrupted. Check Twitch before repeating an action; it may already have succeeded."))
                }
                override fun onResponse(call: Call, response: Response) { continuation.resume(response) { _, value, _ -> value.close() } }
            })
        }
        response.use {
            checkCurrent()
            if (!it.isSuccessful) throw ActionFailure(errorText(it.code))
            val text = it.body.string()
            return try { if (text.isBlank()) buildJsonObject {} else Json.parseToJsonElement(text).jsonObject }
            catch (_: IllegalArgumentException) { throw ActionFailure("Twitch returned an unreadable response. Check the current state before repeating an action.") }
        }
    }

    fun checkCurrent() { if (!current()) throw ActionFailure("The active account changed. Reopen this page.") }
    suspend fun user(login: String): JsonObject {
        require(login.matches(Regex("[A-Za-z0-9_]{1,25}"))) { "Enter a Twitch username, without @ or a URL." }
        return request("users", query = mapOf("login" to login)).rows().firstOrNull()?.jsonObject
            ?: throw ActionFailure("That Twitch username was not found.")
    }
    suspend fun moderated(after: String? = null) = request("moderation/channels", query = buildMap {
        put("user_id", userId); put("first", "100"); after?.let { put("after", it) }
    })
    suspend fun canModerate(channelId: String): Boolean {
        checkCurrent()
        val result = ModeratorPermission.check(userId, channelId) { after ->
            val result = moderated(after)
            ModeratorPermission.Page(result.rows().mapNotNull { (it as? JsonObject)?.text("broadcaster_id")?.takeIf { id -> id.isNotBlank() } }.toSet(), result.cursor())
        }
        checkCurrent()
        return result
    }
    suspend fun requireModerator(channelId: String) {
        if (!canModerate(channelId)) throw ActionFailure("Twitch does not confirm that your active account moderates this channel. No action was submitted.")
    }
    suspend fun userById(id: String): JsonObject = request("users", query = mapOf("id" to id)).rows().firstOrNull()?.jsonObject
        ?: throw ActionFailure("The channel was not found on Twitch.")
    private fun channel(id: String) = mapOf("broadcaster_id" to id, "moderator_id" to userId)
    suspend fun ban(channelId: String, target: String, reason: String, seconds: Int? = null): JsonObject {
        require(seconds == null || seconds in 1..1_209_600) { "Timeout must be between 1 second and 14 days." }
        require(reason.length <= 500) { "Reason must be 500 characters or fewer." }
        return request("moderation/bans", "POST", channel(channelId), buildJsonObject {
            putJsonObject("data") { put("user_id", target); put("reason", reason); seconds?.let { put("duration", it) } }
        })
    }
    suspend fun unban(channelId: String, target: String) = request("moderation/bans", "DELETE", channel(channelId) + ("user_id" to target))
    suspend fun warn(channelId: String, target: String, reason: String): JsonObject {
        require(reason.isNotBlank() && reason.length <= 500) { "Enter a reason of up to 500 characters." }
        return request("moderation/warnings", "POST", channel(channelId), buildJsonObject { putJsonObject("data") { put("user_id", target); put("reason", reason) } })
    }
    suspend fun deleteMessages(channelId: String, messageId: String? = null) = request("moderation/chat", "DELETE", channel(channelId) + buildMap { messageId?.let { put("message_id", it) } })
    suspend fun chatSettings(channelId: String) = request("chat/settings", query = channel(channelId))
    suspend fun chatSetting(channelId: String, key: String, enabled: Boolean, duration: Int? = null): JsonObject {
        require(key in listOf("slow_mode", "follower_mode", "subscriber_mode", "emote_mode", "unique_chat_mode"))
        if (enabled && key == "slow_mode") require(duration != null && duration in 3..120) { "Slow mode requires 3–120 seconds." }
        if (enabled && key == "follower_mode") require(duration != null && duration in 0..129_600) { "Follower age must be 0–129600 minutes." }
        return request("chat/settings", "PATCH", channel(channelId), buildJsonObject {
            put(key, enabled)
            if (enabled && key == "slow_mode") put("slow_mode_wait_time", duration!!)
            if (enabled && key == "follower_mode") put("follower_mode_duration", duration!!)
        })
    }
    suspend fun announcement(channelId: String, text: String): JsonObject {
        require(text.isNotBlank() && text.length <= 500) { "Enter an announcement of up to 500 characters." }
        return request("chat/announcements", "POST", channel(channelId), buildJsonObject { put("message", text); put("color", "primary") })
    }
    suspend fun shield(channelId: String, enabled: Boolean) = request("moderation/shield_mode", "PUT", channel(channelId), buildJsonObject { put("is_active", enabled) })
    suspend fun automodSettings(channelId: String) = request("moderation/automod/settings", query = channel(channelId))
    suspend fun automodLevel(channelId: String, level: Int): JsonObject {
        require(level in 0..4)
        return request("moderation/automod/settings", "PUT", channel(channelId), buildJsonObject { put("overall_level", level) })
    }
    suspend fun automodMessage(channelId: String, messageId: String, allow: Boolean) = request("moderation/automod/message", "POST", moderatorChannel = channelId, body = buildJsonObject {
        put("user_id", userId); put("msg_id", messageId); put("action", if (allow) "ALLOW" else "DENY")
    })
    suspend fun terms(channelId: String, after: String? = null) = request("moderation/blocked_terms", query = channel(channelId) + buildMap {
        put("first", "100"); after?.let { put("after", it) }
    })
    suspend fun addTerm(channelId: String, text: String): JsonObject {
        require(text.length in 2..500) { "A blocked term must contain 2–500 characters." }
        return request("moderation/blocked_terms", "POST", channel(channelId), buildJsonObject { put("text", text) })
    }
    suspend fun removeTerm(channelId: String, id: String) = request("moderation/blocked_terms", "DELETE", channel(channelId) + ("id" to id))
    suspend fun unbanRequests(channelId: String, after: String? = null) = request("moderation/unban_requests", query = channel(channelId) + buildMap {
        put("status", "pending"); put("first", "100"); after?.let { put("after", it) }
    })
    suspend fun resolveUnban(channelId: String, id: String, approve: Boolean, text: String): JsonObject {
        require(text.length <= 500) { "Resolution must be 500 characters or fewer." }
        return request("moderation/unban_requests", "PATCH", channel(channelId) + mapOf("unban_request_id" to id, "status" to if (approve) "approved" else "denied", "resolution_text" to text))
    }
    suspend fun createClip(channelId: String): JsonObject = request("clips", "POST", mapOf("broadcaster_id" to channelId)).rows().firstOrNull()?.jsonObject
        ?: throw ActionFailure("Twitch did not return a clip. Check the channel before trying again.")
    suspend fun whisper(targetId: String, text: String): JsonObject {
        require(text.isNotBlank() && text.length <= 500) { "Enter a whisper of up to 500 characters." }
        return request("whispers", "POST", mapOf("from_user_id" to userId, "to_user_id" to targetId), buildJsonObject { put("message", text) })
    }
    suspend fun subscribe(type: String, condition: JsonObject, sessionId: String) = request("eventsub/subscriptions", "POST", body = subscription(type, condition, sessionId))

    companion object {
        fun subscription(type: String, condition: JsonObject, sessionId: String) = buildJsonObject {
            put("type", type); put("version", "1"); put("condition", condition)
            putJsonObject("transport") { put("method", "websocket"); put("session_id", sessionId) }
        }
        fun errorText(code: Int) = when (code) {
            401 -> "Twitch needs a fresh login. Reconnect this account using the Twitch API login option in Settings."
            403 -> "Twitch refused this action. Your account may lack channel permissions or the required login permission. Reconnect using Twitch API login; check your moderator role and channel restrictions."
            404 -> "Twitch could not find this item. It may have expired or already been handled."
            409 -> "Twitch reports a conflict. Refresh before trying another action."
            429 -> "Twitch's rate limit was reached. Wait before trying again."
            else -> "Twitch could not complete the request (HTTP $code). Check the channel's current state before repeating it."
        }
        fun clipEditUrl(value: String): String? = runCatching {
            val url = value.toHttpUrl()
            value.takeIf { url.scheme == "https" && url.host in setOf("www.twitch.tv", "twitch.tv", "clips.twitch.tv") && url.username.isEmpty() && url.password.isEmpty() && url.port == 443 }
        }.getOrNull()
    }
}

/** Also prevents HTTP 503/421 follow-ups, which connection-retry settings alone do not cover. */
private class SingleSubmissionBody(private val body: RequestBody) : RequestBody() {
    override fun contentType() = body.contentType()
    override fun contentLength() = body.contentLength()
    override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
    override fun isOneShot() = true
}

class ActionFailure(message: String) : IOException(message)
fun JsonObject.rows(): JsonArray = this["data"] as? JsonArray ?: JsonArray(emptyList())
fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonObject.cursor(): String? = (this["pagination"] as? JsonObject)?.text("cursor")?.takeIf { it.isNotBlank() }
internal fun JsonObject.heldMessageText(): String = when (val message = this["message"]) {
    is JsonObject -> message.text("text")
    is JsonPrimitive -> message.contentOrNull.orEmpty()
    else -> ""
}
