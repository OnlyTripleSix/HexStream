package com.github.andreyasadchy.xtra.repository

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import okio.Buffer

class ChannelActionsApiTest {
    private class Fixture(var active: Boolean = true, var code: Int = 200, var response: String = "{}", headers: Map<String, String> = mapOf("Client-Id" to "fixture", "Authorization" to "Bearer fixture")) {
        val requests = mutableListOf<Request>()
        var onRequest: () -> Unit = {}
        val api = ChannelActionsApi(OkHttpClient(), "11", headers) { active }.apply {
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                requests += chain.request(); onRequest()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Fixture").body(response.toResponseBody()).build()
            }.build()
        }
        fun body() = Json.parseToJsonElement(Buffer().also { requests.last().body!!.writeTo(it) }.readUtf8()).jsonObject
    }
    @Test fun permanentBanOmitsDurationAndUsesActiveModerator() = runBlocking {
        val f = Fixture(); f.api.ban("22", "33", "Reason")
        val req = f.requests.single()
        assertEquals("POST", req.method); assertEquals("/helix/moderation/bans", req.url.encodedPath)
        assertEquals("11", req.url.queryParameter("moderator_id")); assertEquals("22", req.url.queryParameter("broadcaster_id"))
        assertFalse(f.body()["data"]!!.jsonObject.containsKey("duration"))
        assertEquals("33", f.body()["data"]!!.jsonObject.text("user_id"))
    }
    @Test fun timeoutRequiresSafeBoundsBeforeAnyRequest() {
        val f = Fixture()
        listOf(0, -1, 1_209_601).forEach { duration -> assertThrows(IllegalArgumentException::class.java) { runBlocking { f.api.ban("22", "33", "", duration) } } }
        assertTrue(f.requests.isEmpty())
        runBlocking { f.api.ban("22", "33", "", 1_209_600) }
        assertEquals(1_209_600, f.body()["data"]!!.jsonObject["duration"]!!.jsonPrimitive.int)
    }
    @Test fun staleAccountCannotSubmit() {
        val f = Fixture(active = false)
        assertThrows(ActionFailure::class.java) { runBlocking { f.api.unban("22", "33") } }
        assertTrue(f.requests.isEmpty())
    }
    @Test fun accountChangedDuringRequestCannotReturnOtherAccountsData() {
        val f = Fixture(response = """{"data":[{"id":"33"}]}"""); f.onRequest = { f.active = false }
        assertThrows(ActionFailure::class.java) { runBlocking { f.api.user("example") } }
        assertEquals(1, f.requests.size)
    }
    @Test fun errorsNeverExposePrivateResponseBodiesOrRetryMutations() {
        listOf(400, 401, 403, 409, 429, 503).forEach { code ->
            val f = Fixture(code = code, response = "private response")
            val error = assertThrows(ActionFailure::class.java) { runBlocking { f.api.warn("22", "33", "Reason") } }
            assertFalse(error.message!!.contains("private response")); assertEquals(1, f.requests.size)
        }
    }
    @Test fun mutationClientHasNoLoggingRedirectsOrAutomaticRetries() {
        val original = OkHttpClient.Builder().addInterceptor { error("Must not be used") }.addNetworkInterceptor { error("Must not be used") }.build()
        val client = ChannelActionsApi(original, "11", emptyMap()) { true }.client
        assertTrue(client.interceptors.isEmpty()); assertTrue(client.networkInterceptors.isEmpty())
        assertFalse(client.retryOnConnectionFailure); assertFalse(client.followRedirects); assertFalse(client.followSslRedirects)
    }
    @Test fun invalidCredentialHeadersNeverAppearInAnError() {
        val f = Fixture(headers = mapOf("Authorization" to "Bearer fixture\nbad"))
        val error = assertThrows(ActionFailure::class.java) { runBlocking { f.api.user("example") } }
        assertFalse(error.message!!.contains("fixture")); assertTrue(f.requests.isEmpty())
    }
    @Test fun malformedSuccessResponseNeverAppearsInAnError() {
        val f = Fixture(response = "private response")
        val error = assertThrows(ActionFailure::class.java) { runBlocking { f.api.user("example") } }
        assertFalse(error.message!!.contains("private response")); assertEquals(1, f.requests.size)
    }
    @Test fun clipCreationWorksForAnyRequestedChannelWithoutFollowOrModRequest() = runBlocking {
        val f = Fixture(response = """{"data":[{"id":"clip","edit_url":"https://clips.twitch.tv/Example/edit"}]}""")
        val clip = f.api.createClip("22")
        assertEquals("clip", clip.text("id")); assertEquals(1, f.requests.size)
        assertEquals("/helix/clips", f.requests.single().url.encodedPath)
        assertEquals("22", f.requests.single().url.queryParameter("broadcaster_id"))
    }
    @Test fun clipEditorRejectsLookalikeHostsAndCredentials() {
        listOf("https://clips.twitch.tv/Example/edit", "https://www.twitch.tv/example/clip/Example").forEach { assertNotNull(ChannelActionsApi.clipEditUrl(it)) }
        listOf("http://clips.twitch.tv/Example/edit", "https://clips.twitch.tv.evil.test/edit", "https://twitch.tv@evil.test/", "https://name@twitch.tv/", "javascript:alert(1)", "https://twitch.tv:8080/").forEach { assertNull(ChannelActionsApi.clipEditUrl(it)) }
    }
    @Test fun whisperUsesActiveSenderAndPreservesText() = runBlocking {
        val f = Fixture(code = 204, response = "")
        f.api.whisper("33", "Hello \"friend\"\n👋")
        assertEquals("11", f.requests.single().url.queryParameter("from_user_id"))
        assertEquals("33", f.requests.single().url.queryParameter("to_user_id"))
        assertEquals("Hello \"friend\"\n👋", f.body().text("message"))
    }
    @Test fun whisperAndWarningValidationPreventsEmptyOrOversizeSubmissions() {
        val f = Fixture()
        listOf("", " ", "a".repeat(501)).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { runBlocking { f.api.whisper("33", value) } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { f.api.warn("22", "33", value) } }
        }
        assertTrue(f.requests.isEmpty())
    }
    @Test fun changedChatModeDoesNotOverwriteOtherModes() = runBlocking {
        val f = Fixture(); f.api.chatSetting("22", "slow_mode", true, 10)
        assertEquals(setOf("slow_mode", "slow_mode_wait_time"), f.body().keys)
        f.api.chatSetting("22", "follower_mode", false)
        assertEquals(setOf("follower_mode"), f.body().keys)
    }
    @Test fun chatModeRejectsInvalidDurationsAndUnknownFields() {
        val f = Fixture()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { f.api.chatSetting("22", "slow_mode", true, 2) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { f.api.chatSetting("22", "follower_mode", true, -1) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { f.api.chatSetting("22", "unknown", true) } }
        assertTrue(f.requests.isEmpty())
    }
    @Test fun automodDecisionUsesActiveModeratorAndExpectedAction() = runBlocking {
        val f = Fixture(); f.api.automodMessage("held-message", false)
        assertEquals("11", f.body().text("user_id")); assertEquals("held-message", f.body().text("msg_id")); assertEquals("DENY", f.body().text("action"))
    }
    @Test fun automodQueueDisplaysBothDocumentedMessageShapes() {
        val v1 = Json.parseToJsonElement("""{"message":"Held message"}""").jsonObject
        val v2 = Json.parseToJsonElement("""{"message":{"text":"Held message"}}""").jsonObject
        assertEquals("Held message", v1.heldMessageText())
        assertEquals("Held message", v2.heldMessageText())
        assertEquals("", buildJsonObject {}.heldMessageText())
    }
    @Test fun deletingOneMessageCannotClearWholeChatByAccident() = runBlocking {
        val f = Fixture(); f.api.deleteMessages("22", "selected-id")
        assertEquals("selected-id", f.requests.last().url.queryParameter("message_id"))
        f.api.deleteMessages("22")
        assertNull(f.requests.last().url.queryParameter("message_id"))
    }
    @Test fun whisperSubscriptionUsesUserConditionWithoutBroadcaster() {
        val body = ChannelActionsApi.subscription("user.whisper.message", buildJsonObject { put("user_id", "11") }, "session")
        assertEquals(setOf("user_id"), body["condition"]!!.jsonObject.keys)
        assertEquals("websocket", body["transport"]!!.jsonObject.text("method"))
        assertEquals("1", body.text("version"))
    }
    @Test fun reconnectHandoffCannotNavigateToOtherHosts() {
        assertTrue(TwitchEvents.validReconnectUrl("wss://eventsub.wss.twitch.tv/ws?session=fixture"))
        listOf("wss://evil.test/ws", "ws://eventsub.wss.twitch.tv/ws", "wss://name@eventsub.wss.twitch.tv/ws", "wss://eventsub.wss.twitch.tv:8080/ws").forEach { assertFalse(TwitchEvents.validReconnectUrl(it)) }
    }
    @Test fun unbanResolutionAndCursorAreEncodedInQuery() = runBlocking {
        val f = Fixture(); f.api.resolveUnban("22", "request-id", true, "Approved & reviewed")
        assertEquals("approved", f.requests.last().url.queryParameter("status"))
        assertEquals("Approved & reviewed", f.requests.last().url.queryParameter("resolution_text"))
        f.api.moderated("next+cursor")
        assertEquals("next+cursor", f.requests.last().url.queryParameter("after"))
    }
}
