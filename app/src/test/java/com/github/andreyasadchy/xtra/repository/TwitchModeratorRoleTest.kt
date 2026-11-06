package com.github.andreyasadchy.xtra.repository

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import okio.Buffer

class TwitchModeratorRoleTest {
    private class Fixture {
        var active = true
        var owner = "11"
        var channel = "22"
        var allowed: Boolean? = true
        var code = 200
        var errors = false
        var afterRequest: () -> Unit = {}
        val requests = mutableListOf<Request>()
        val role = TwitchModeratorRole(OkHttpClient(), "11", mapOf("Client-ID" to "fixture", "Authorization" to "OAuth fixture")) { active }.apply {
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                requests += chain.request()
                afterRequest()
                val body = buildJsonObject {
                    if (errors) putJsonArray("errors") { add(buildJsonObject { put("message", "private fixture diagnostic") }) }
                    putJsonObject("data") {
                        putJsonObject("currentUser") { put("id", owner) }
                        putJsonObject("user") { put("id", channel); putJsonObject("self") { allowed?.let { put("isModerator", it) } } }
                    }
                }.toString()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Fixture").body(body.toResponseBody()).build()
            }.build()
        }
    }

    @Test fun ownedExactChannelRoleUsesReadOnlyDocument() = runBlocking {
        val f = Fixture()
        assertTrue(f.role.check("22"))
        val request = f.requests.single()
        val body = Json.parseToJsonElement(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).jsonObject
        assertTrue(body.text("query").startsWith("query "))
        assertFalse(body.text("query").contains("mutation"))
        assertEquals("22", body["variables"]!!.jsonObject.text("id"))
        assertEquals("gql.twitch.tv", request.url.host)
    }
    @Test fun anotherChannelsOrAccountsRoleIsRejected() {
        for (mismatch in listOf("owner", "channel")) {
            val f = Fixture()
            if (mismatch == "owner") f.owner = "99" else f.channel = "33"
            assertThrows(ActionFailure::class.java) { runBlocking { f.role.check("22") } }
        }
    }
    @Test fun explicitNotModeratorIsNotAnError() = runBlocking {
        val f = Fixture(); f.allowed = false
        assertFalse(f.role.check("22"))
    }
    @Test fun channelOwnerStillRequiresMatchingWebAccountAndChannelIdentity() = runBlocking {
        val f = Fixture(); f.channel = "11"; f.allowed = false
        assertTrue(f.role.check("11"))
        f.owner = "99"
        assertThrows(ActionFailure::class.java) { runBlocking { f.role.check("11") } }
        assertEquals(2, f.requests.size)
    }
    @Test fun missingRoleAndGraphErrorsDoNotGrantAccess() {
        val missing = Fixture(); missing.allowed = null
        assertThrows(ActionFailure::class.java) { runBlocking { missing.role.check("22") } }
        val failed = Fixture(); failed.errors = true
        val error = assertThrows(ActionFailure::class.java) { runBlocking { failed.role.check("22") } }
        assertFalse(error.message.orEmpty().contains("private fixture diagnostic"))
    }
    @Test fun accountChangeBeforeOrDuringLookupIsRejected() {
        val before = Fixture(); before.active = false
        assertThrows(ActionFailure::class.java) { runBlocking { before.role.check("22") } }
        assertTrue(before.requests.isEmpty())
        val during = Fixture(); during.afterRequest = { during.active = false }
        assertThrows(ActionFailure::class.java) { runBlocking { during.role.check("22") } }
    }
    @Test fun failedWebAuthenticationHasUsefulSetupError() {
        val f = Fixture(); f.code = 403
        val error = assertThrows(ActionFailure::class.java) { runBlocking { f.role.check("22") } }
        assertTrue(error.message.orEmpty().contains("Reconnect"))
    }
    @Test fun oldApiGrantCanUseOwnedRoleThenRecheckBeforeBan() = runBlocking {
        val f = Fixture()
        val writes = mutableListOf<Request>()
        val api = ChannelActionsApi(OkHttpClient(), "11", emptyMap(), roleFallback = f.role::check) { f.active }.apply {
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                val roleRequest = chain.request().url.encodedPath.endsWith("moderation/channels")
                if (!roleRequest) writes += chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (roleRequest) 403 else 200).message("Fixture").body("{}".toResponseBody()).build()
            }.build()
        }
        assertTrue(api.canModerate("22"))
        api.ban("22", "33", "")
        assertEquals(2, f.requests.size)
        assertEquals(1, writes.size)
        f.allowed = false
        assertThrows(ActionFailure::class.java) { runBlocking { api.ban("22", "33", "") } }
        assertEquals(1, writes.size)
    }
    @Test fun unavailableRoleServiceDoesNotTriggerAnotherAuthenticationPath() {
        var fallbackCalls = 0
        val api = ChannelActionsApi(OkHttpClient(), "11", emptyMap(), roleFallback = { fallbackCalls++; true }) { true }.apply {
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("Fixture").body("{}".toResponseBody()).build()
            }.build()
        }
        assertThrows(ActionFailure::class.java) { runBlocking { api.canModerate("22") } }
        assertEquals(0, fallbackCalls)
    }
}
