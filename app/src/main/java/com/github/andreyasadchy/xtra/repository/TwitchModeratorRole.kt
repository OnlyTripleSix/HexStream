package com.github.andreyasadchy.xtra.repository

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Read the active web account's own role when an older API grant lacks channel-list access. */
internal class TwitchModeratorRole(client: OkHttpClient, private val owner: String, private val headers: Map<String, String>, private val current: () -> Boolean) {
    internal var callFactory: Call.Factory = client.newBuilder().apply {
        interceptors().clear()
        networkInterceptors().clear()
        retryOnConnectionFailure(false)
        followRedirects(false)
        followSslRedirects(false)
    }.build()

    suspend fun check(channelId: String): Boolean {
        require(channelId.matches(Regex("[0-9]+")))
        checkCurrent()
        val payload = buildJsonObject {
            put("query", "query HexStreamModeratorRole(\$id:ID!){currentUser{id}user(id:\$id){id self{isModerator}}}")
            putJsonObject("variables") { put("id", channelId) }
        }
        val request = Request.Builder().url("https://gql.twitch.tv/gql").apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        val response = suspendCancellableCoroutine { continuation ->
            val call = callFactory.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(ActionFailure("Could not verify moderator access. Check the connection and retry."))
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        response.use {
            checkCurrent()
            if (!it.isSuccessful) throw ActionFailure("Reconnect the active account using Both APIs to enable moderator controls.")
            val root = Json.parseToJsonElement(it.body.string()).jsonObject
            if ((root["errors"] as? JsonArray)?.isNotEmpty() == true) throw ActionFailure("Twitch could not verify your web login. Reconnect the active account and retry.")
            val data = root["data"] as? JsonObject ?: throw ActionFailure("Twitch returned incomplete moderator details.")
            val currentUser = data["currentUser"] as? JsonObject
            if (currentUser?.text("id") != owner) throw ActionFailure("The Twitch web login does not match your active account. Reconnect it before moderating.")
            val channel = data["user"] as? JsonObject
            if (channel?.text("id") != channelId) throw ActionFailure("Twitch returned a different channel. Reopen the channel controls.")
            if (owner == channelId) return true
            val self = channel["self"] as? JsonObject
            return (self?.get("isModerator") as? JsonPrimitive)?.booleanOrNull
                ?: throw ActionFailure("Twitch did not confirm your channel role. Reconnect the account and retry.")
        }
    }

    private fun checkCurrent() {
        if (!current()) throw ActionFailure("The active account changed. Reopen the channel controls.")
    }
}
