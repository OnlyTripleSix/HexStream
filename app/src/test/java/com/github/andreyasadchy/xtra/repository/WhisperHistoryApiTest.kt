package com.github.andreyasadchy.xtra.repository

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class WhisperHistoryApiTest {
    private fun message(id: String, from: String = "22", date: String = "2024-01-02T03:04:05Z", deleted: Boolean = false) = buildJsonObject {
        put("id", id); put("sentAt", date); if (deleted) put("deletedAt", date) else put("deletedAt", JsonNull)
        putJsonObject("from") { put("id", from) }; putJsonObject("content") { put("content", "Hello\n👋") }
    }
    private fun thread(messages: List<JsonObject> = listOf(message("m1")), owner: String = "11", peer: String = "22", id: String = "thread") = buildJsonObject {
        put("id", id); put("isArchived", false)
        putJsonArray("participants") {
            add(buildJsonObject { put("id", owner); put("login", "owner"); put("displayName", "Owner") })
            add(buildJsonObject { put("id", peer); put("login", "peer"); put("displayName", "Peer") })
        }
        putJsonObject("messages") { putJsonArray("edges") { messages.forEach { msg -> add(buildJsonObject { put("cursor", "cursor-${msg.text("id")}"); put("node", msg) }) } } }
    }
    private fun response(node: JsonObject = thread(), owner: String = "11", list: Boolean = true) = buildJsonObject {
        putJsonObject("data") {
            putJsonObject("currentUser") {
                put("id", owner); put("login", "owner")
                if (list) putJsonObject("whisperThreads") { putJsonArray("edges") { add(buildJsonObject { put("cursor", "thread-page"); put("node", node) }) } }
            }
            if (!list) put("whisperThread", node)
        }
    }.toString()
    private class Fixture(var body: String, var active: Boolean = true) {
        val requests = mutableListOf<Request>()
        var onRequest: () -> Unit = {}
        val api = WhisperHistoryApi(OkHttpClient(), "11", mapOf("Client-ID" to "fixture", "Authorization" to "OAuth fixture")) { active }.apply {
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                requests += chain.request(); onRequest()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture").body(body.toResponseBody()).build()
            }.build()
        }
        fun requestBody() = Json.parseToJsonElement(Buffer().also { requests.last().body!!.writeTo(it) }.readUtf8()).jsonObject
    }
    @Test fun existingThreadsUseOwnedWebHistoryAndPreserveText() = runBlocking {
        val f = Fixture(response()); val page = f.api.threads(null)
        assertEquals("thread", page.threads.single().id); assertEquals("22", page.threads.single().peer.id)
        assertEquals("Hello\n👋", page.threads.single().messages.single().text)
        assertEquals("thread-page", page.next)
        assertEquals("https://gql.twitch.tv/gql", f.requests.single().url.toString())
        assertFalse(f.requestBody().text("query").contains("mutation", true))
    }
    @Test fun messagePagingPassesExactThreadAndCursor() = runBlocking {
        val f = Fixture(response(list = false)); val thread = f.api.thread("thread", "22", "older+cursor")
        val vars = f.requestBody()["variables"]!!.jsonObject
        assertEquals("thread", vars.text("id")); assertEquals("older+cursor", vars.text("cursor"))
        assertEquals("cursor-m1", thread.next)
    }
    @Test fun anotherAccountsHistoryIsNeverReturned() {
        val f = Fixture(response(owner = "99"))
        assertThrows(ActionFailure::class.java) { runBlocking { f.api.threads(null) } }
    }
    @Test fun wrongThreadOrRecipientIsRejected() {
        val f = Fixture(response(list = false))
        assertThrows(ActionFailure::class.java) { runBlocking { f.api.thread("different", "22", null) } }
        assertThrows(ActionFailure::class.java) { runBlocking { f.api.thread("thread", "99", null) } }
    }
    @Test fun missingOwnerInParticipantsIsRejected() {
        assertThrows(ActionFailure::class.java) { WhisperHistoryApi.parseThread(thread(owner = "99"), "11") }
    }
    @Test fun unavailableConversationDoesNotHideValidPastConversations() = runBlocking {
        val root = Json.parseToJsonElement(response()).jsonObject
        val current = root["data"]!!.jsonObject["currentUser"]!!.jsonObject
        val valid = current["whisperThreads"]!!.jsonObject["edges"]!!.jsonArray.first()
        val withUnavailable = buildJsonObject { putJsonObject("data") { putJsonObject("currentUser") {
            put("id", "11")
            putJsonObject("whisperThreads") { putJsonArray("edges") { add(valid); add(buildJsonObject { put("cursor", "older"); put("node", thread(owner = "99", id = "unavailable")) }) } }
        } } }
        val page = Fixture(withUnavailable.toString()).api.threads(null)
        assertEquals(listOf("thread"), page.threads.map { it.id }); assertEquals(1, page.unavailable); assertEquals("older", page.next)
    }
    @Test fun messageFromAnotherParticipantIsRejected() {
        assertThrows(ActionFailure::class.java) { WhisperHistoryApi.parseThread(thread(listOf(message("m1", from = "99"))), "11") }
    }
    @Test fun deletedMessagesAreRemovedAndReportedForCacheInvalidation() {
        val parsed = WhisperHistoryApi.parseThread(thread(listOf(message("deleted", deleted = true), message("kept"))), "11")
        assertEquals(listOf("kept"), parsed.messages.map { it.id }); assertEquals(setOf("deleted"), parsed.deleted)
    }
    @Test fun duplicateMessagesCollapseAndOlderPagesAreChronological() {
        val parsed = WhisperHistoryApi.parseThread(thread(listOf(message("new", date = "2025-01-01T00:00:00Z"), message("old"), message("new", date = "2025-01-01T00:00:00Z"))), "11")
        assertEquals(listOf("old", "new"), parsed.messages.map { it.id })
    }
    @Test fun accountChangeBeforeOrDuringReadCannotImportAnything() {
        val f = Fixture(response(), active = false)
        assertThrows(ActionFailure::class.java) { runBlocking { f.api.threads(null) } }; assertTrue(f.requests.isEmpty())
        f.active = true; f.onRequest = { f.active = false }
        assertThrows(ActionFailure::class.java) { runBlocking { f.api.threads(null) } }; assertEquals(1, f.requests.size)
    }
    @Test fun missingLoginAndServerErrorsAreNotAnEmptyInbox() {
        val f = Fixture("""{"data":{"currentUser":null}}""")
        assertThrows(ActionFailure::class.java) { runBlocking { f.api.threads(null) } }
        f.body = """{"errors":[{"message":"private payload"}]}"""
        val error = assertThrows(ActionFailure::class.java) { runBlocking { f.api.threads(null) } }
        assertFalse(error.message!!.contains("private payload"))
        val diagnosis = WhisperHistoryApi.historyFailure(Json.parseToJsonElement("""{"errors":[{"message":"invalid cursor private-payload","path":["whisperThread","private-recipient","messages"]}]}""").jsonObject)
        assertTrue(diagnosis.contains("cursor")); assertTrue(diagnosis.contains("messages"))
        assertFalse(diagnosis.contains("private"))
        assertEquals(WhisperHistoryApi.VERIFICATION_REQUIRED, WhisperHistoryApi.historyFailure(Json.parseToJsonElement("""{"errors":[{"message":"failed integrity check"}]}""").jsonObject))
    }
    @Test fun malformedResponsesAreNotDisplayedOrTreatedAsEmpty() {
        val f = Fixture("private payload")
        val error = assertThrows(ActionFailure::class.java) { runBlocking { f.api.threads(null) } }
        assertFalse(error.message!!.contains("private payload"))
    }
    @Test fun historyTransportHasNoLoggingOrCredentialRedirects() {
        val api = WhisperHistoryApi(OkHttpClient.Builder().addInterceptor { error("Must not be used") }.build(), "11", emptyMap()) { true }
        val client = api.callFactory as OkHttpClient
        assertTrue(client.interceptors.isEmpty()); assertTrue(client.networkInterceptors.isEmpty())
        assertFalse(client.followRedirects); assertFalse(client.followSslRedirects)
    }
}
