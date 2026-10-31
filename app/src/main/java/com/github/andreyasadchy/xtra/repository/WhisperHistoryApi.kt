package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.util.WhisperEntry
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Instant

data class WhisperPeer(val id: String, val login: String, val name: String)
data class WhisperThread(val id: String, val peer: WhisperPeer, val messages: List<WhisperEntry>, val next: String?, val deleted: Set<String> = emptySet(), val archived: Boolean = false)
data class WhisperThreadsPage(val threads: List<WhisperThread>, val next: String?, val unavailable: Int = 0)

/** Read-only web-client history, bound to the active account. No cookie extraction or request logging. */
class WhisperHistoryApi(client: OkHttpClient, val accountId: String, private val headers: Map<String, String>, private val current: () -> Boolean) {
    private val client = client.newBuilder().apply {
        interceptors().clear(); networkInterceptors().clear()
        retryOnConnectionFailure(false); followRedirects(false); followSslRedirects(false)
    }.build()
    internal var callFactory: Call.Factory = this.client
    fun checkCurrent() { if (!current()) throw ActionFailure("The active account changed. Reopen Whispers.") }
    private suspend fun query(document: String, variables: JsonObject): JsonObject {
        checkCurrent()
        val body = buildJsonObject { put("query", document); put("variables", variables) }.toString()
        val request = try { Request.Builder().url("https://gql.twitch.tv/gql").apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.post(body.toRequestBody("application/json".toMediaType())).build() }
        catch (_: IllegalArgumentException) { throw ActionFailure(LOGIN_REQUIRED) }
        val response = suspendCancellableCoroutine { continuation ->
            val call = callFactory.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(ActionFailure("Could not load Twitch history. Your saved messages are kept; tap Refresh to retry.")) }
                override fun onResponse(call: Call, response: Response) { continuation.resume(response) { _, value, _ -> value.close() } }
            })
        }
        response.use {
            checkCurrent()
            if (it.code == 401 || it.code == 403) throw ActionFailure(LOGIN_REQUIRED)
            if (!it.isSuccessful) throw ActionFailure("Twitch history is unavailable (HTTP ${it.code}). Your saved messages are kept.")
            val root = try { Json.parseToJsonElement(it.body.string()).jsonObject }
            catch (_: IllegalArgumentException) { throw ActionFailure("Twitch returned unreadable history. Your saved messages are kept.") }
            if ((root["errors"] as? JsonArray)?.isNotEmpty() == true) throw ActionFailure(historyFailure(root))
            val data = root["data"] as? JsonObject ?: throw ActionFailure("Twitch returned no history data. Tap Refresh to retry.")
            val user = data["currentUser"] as? JsonObject ?: throw ActionFailure(LOGIN_REQUIRED)
            if (user.text("id") != accountId) throw ActionFailure("Twitch returned a different account. History was not imported. Reconnect your active account.")
            return data
        }
    }
    suspend fun threads(after: String?): WhisperThreadsPage {
        val data = query(THREADS, buildJsonObject { after?.let { put("cursor", it) } })
        val connection = data["currentUser"]!!.jsonObject["whisperThreads"] as? JsonObject
            ?: throw ActionFailure("Twitch did not return its conversation list. Your saved messages are kept.")
        val edges = connection["edges"] as? JsonArray ?: throw ActionFailure("Twitch returned an incomplete conversation list.")
        var unavailable = 0
        val threads = edges.mapNotNull { edge ->
            val node = (edge as? JsonObject)?.get("node") as? JsonObject
            if (node == null) { unavailable++; null }
            else try { parseThread(node, accountId) } catch (_: ActionFailure) { unavailable++; null }
        }
        val cursor = (edges.lastOrNull() as? JsonObject)?.text("cursor")?.takeIf { it.isNotBlank() }
        return WhisperThreadsPage(threads, cursor, unavailable)
    }
    suspend fun thread(id: String, peerId: String, after: String?): WhisperThread {
        val data = query(MESSAGES, buildJsonObject { put("id", id); after?.let { put("cursor", it) } })
        val node = data["whisperThread"] as? JsonObject ?: throw ActionFailure("Twitch did not return this conversation. Your saved messages are kept.")
        if (node.text("id") != id) throw ActionFailure("Twitch returned a different conversation. Its messages were not imported.")
        return parseThread(node, accountId).also { if (it.peer.id != peerId) throw ActionFailure("This conversation belongs to a different recipient. Its messages were not imported.") }
    }
    suspend fun user(login: String): WhisperPeer {
        require(login.matches(Regex("[A-Za-z0-9_]{1,25}"))) { "Enter a Twitch username, without @ or a URL." }
        val data = query(USER, buildJsonObject { put("login", login) })
        val user = data["user"] as? JsonObject ?: throw ActionFailure("That Twitch username was not found.")
        return WhisperPeer(user.text("id"), user.text("login"), user.text("displayName"))
    }
    companion object {
        const val LOGIN_REQUIRED = "Older conversations need your Twitch login. Choose Both under Settings → API token settings → Get token for selected APIs, then log in again."
        const val VERIFICATION_REQUIRED = "Twitch requires a verification check for this history page. Tap Verify Twitch login, then retry the page."
        /** Fixed diagnostic words only: never expose server messages, IDs, cursors or response bodies. */
        internal fun historyFailure(root: JsonObject): String {
            val errors = (root["errors"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            if (errors.any { it.text("message").equals("failed integrity check", true) }) return VERIFICATION_REQUIRED
            val categories = listOf("cursor", "authorization", "authentication", "integrity", "timeout", "internal", "rate", "limit", "validation", "null", "decode", "unmarshal", "permission", "not found")
                .filter { word -> errors.any { it.text("message").contains(word, true) } }
            val paths = errors.flatMap { (it["path"] as? JsonArray).orEmpty() }.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .filter { it in setOf("currentUser", "whisperThreads", "whisperThread", "messages", "content", "from", "sentAt", "deletedAt") }.distinct()
            val detail = (categories + paths).distinct().joinToString(", ").ifBlank { "request failed" }
            return "Twitch could not load this history page ($detail). Saved messages are kept; retry the page or Refresh."
        }
        private const val MESSAGE_FIELDS = "id sentAt deletedAt from { id } content { content }"
        private const val THREAD_FIELDS = "id participants { id login displayName } isArchived messages(first:1) { edges { cursor node { $MESSAGE_FIELDS } } }"
        internal const val THREADS = "query HexStreamWhisperThreads(\$cursor:Cursor){currentUser{id login whisperThreads(first:10,after:\$cursor){edges{cursor node{$THREAD_FIELDS}}}}}"
        internal const val MESSAGES = "query HexStreamWhisperMessages(\$id:ID!,\$cursor:Cursor){currentUser{id}whisperThread(id:\$id){id participants{id login displayName}isArchived messages(first:20,after:\$cursor){edges{cursor node{$MESSAGE_FIELDS}}}}}"
        internal const val USER = "query HexStreamWhisperUser(\$login:String!){currentUser{id}user(login:\$login){id login displayName}}"
        internal fun parseThread(node: JsonObject, owner: String): WhisperThread {
            val participants = (node["participants"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            if (participants.size != 2 || participants.map { it.text("id") }.toSet().size != 2 || participants.none { it.text("id") == owner }) throw ActionFailure("This conversation could not be verified for your account.")
            val peer = participants.single { it.text("id") != owner }.let { WhisperPeer(it.text("id"), it.text("login"), it.text("displayName")) }
            if (peer.id.isBlank() || node.text("id").isBlank()) throw ActionFailure("Twitch returned an incomplete conversation.")
            val names = participants.associate { it.text("id") to it.text("displayName").ifBlank { it.text("login") } }
            val edges = (node["messages"] as? JsonObject)?.get("edges") as? JsonArray ?: throw ActionFailure("Twitch returned an incomplete message page.")
            val deleted = mutableSetOf<String>()
            val messages = edges.mapNotNull { edge ->
                val message = (edge as? JsonObject)?.get("node") as? JsonObject ?: return@mapNotNull null
                val id = message.text("id"); if (id.isBlank()) throw ActionFailure("A Twitch message could not be verified.")
                if (message.text("deletedAt").isNotBlank()) { deleted += id; return@mapNotNull null }
                val from = (message["from"] as? JsonObject)?.text("id").orEmpty()
                if (from !in names) throw ActionFailure("A message belongs to a different conversation and was not imported.")
                val to = if (from == owner) peer.id else owner
                val timestamp = Instant.parseOrNull(message.text("sentAt"))?.toEpochMilliseconds()
                    ?: throw ActionFailure("A Twitch message has an unreadable date.")
                val content = (message["content"] as? JsonObject)?.text("content") ?: throw ActionFailure("A Twitch message could not be read.")
                WhisperEntry(id, from, names.getValue(from), to, names.getValue(to), content, timestamp)
            }.distinctBy { it.id }.sortedWith(compareBy<WhisperEntry> { it.time }.thenBy { it.id })
            return WhisperThread(node.text("id"), peer, messages, (edges.lastOrNull() as? JsonObject)?.text("cursor")?.takeIf { it.isNotBlank() }, deleted, (node["isArchived"] as? JsonPrimitive)?.booleanOrNull == true)
        }
    }
}
