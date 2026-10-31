package com.github.andreyasadchy.xtra.util

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class PrototypeAccountsDeviceTest {
    @Test fun savedAccountsRestoreCredentialsWithoutMixingAndKeepGlobalTags() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Run fixture tests only in the isolated QA app", "com.github.andreyasadchy.xtra.qa", c.packageName)
        val vault = c.getSharedPreferences("account_vault", Context.MODE_PRIVATE)
        // Fixture credentials stay in the isolated QA package; never target the user's prototype.
        assertTrue("Use a clean prototype installation for this test", PrototypeAccounts.accounts(c).isEmpty())
        val originalTokens = c.tokenPrefs().all
        val originalClient = c.prefs().getString(C.HELIX_CLIENT_ID, null)
        val originalTags = c.prefs().getStringSet(BlockedTags.KEY, emptySet())!!.toSet()
        try {
            c.tokenPrefs().edit().clear().putString(C.USER_ID, "990000001").putString(C.USERNAME, "fixture_a")
                .putString(C.TOKEN, "test_token_A").putString(C.GQL_TOKEN2, "test_gql_A")
                .putString(C.GQL_HEADERS, org.json.JSONObject().put(C.HEADER_TOKEN, "OAuth test_gql_A")
                    .put(C.HEADER_CLIENT_ID, "client_gql_A").put("Client-Integrity", "stale_proof").toString())
                .putLong(C.INTEGRITY_EXPIRATION, 12345)
                .putString(WhisperHistorySession.VERIFIED_HEADERS, "fixture-proof")
                .putLong(WhisperHistorySession.VERIFIED_UNTIL, Long.MAX_VALUE).commit()
            c.prefs().edit().putString(C.HELIX_CLIENT_ID, "client_A").putStringSet(BlockedTags.KEY, setOf("vtuber")).commit()
            PrototypeAccounts.save(c)
            c.tokenPrefs().edit().clear().putString(C.USER_ID, "990000002").putString(C.USERNAME, "fixture_b")
                .putString(C.TOKEN, "test_token_B").commit()
            c.prefs().edit().putString(C.HELIX_CLIENT_ID, "client_B").commit()
            PrototypeAccounts.save(c)
            assertEquals(2, PrototypeAccounts.accounts(c).size)
            assertFalse(vault.all.values.joinToString().contains("test_token"))
            assertFalse(vault.all.values.joinToString().contains("fixture_a"))
            PrototypeAccounts.activate(c, "990000001")
            assertEquals("test_token_A", c.tokenPrefs().getString(C.TOKEN, null))
            assertEquals("test_gql_A", c.tokenPrefs().getString(C.GQL_TOKEN2, null))
            assertEquals("client_A", c.prefs().getString(C.HELIX_CLIENT_ID, null))
            val restoredHeaders = org.json.JSONObject(c.tokenPrefs().getString(C.GQL_HEADERS, null)!!)
            assertEquals("OAuth test_gql_A", restoredHeaders.getString(C.HEADER_TOKEN))
            assertFalse(restoredHeaders.has("Client-Integrity"))
            assertEquals(0L, c.tokenPrefs().getLong(C.INTEGRITY_EXPIRATION, -1))
            assertFalse(c.tokenPrefs().contains(WhisperHistorySession.VERIFIED_HEADERS))
            assertFalse(c.tokenPrefs().contains(WhisperHistorySession.VERIFIED_UNTIL))
            PrototypeAccounts.activate(c, "990000002")
            assertEquals("test_token_B", c.tokenPrefs().getString(C.TOKEN, null))
            assertNull(c.tokenPrefs().getString(C.GQL_TOKEN2, null))
            assertNull(c.tokenPrefs().getString(C.GQL_HEADERS, null))
            assertEquals("client_B", c.prefs().getString(C.HELIX_CLIENT_ID, null))
            assertEquals(setOf("vtuber"), c.prefs().getStringSet(BlockedTags.KEY, emptySet()))
            PrototypeAccounts.activate(c, null)
            assertNull(c.tokenPrefs().getString(C.USER_ID, null))
            assertEquals(2, PrototypeAccounts.accounts(c).size)
            PrototypeAccounts.activate(c, "990000001")
            assertTrue(PrototypeAccounts.removeActive(c))
            assertEquals(listOf("990000002"), PrototypeAccounts.accounts(c).map { it.first })
        } finally {
            vault.edit().remove("990000001").remove("990000002").commit()
            val e = c.tokenPrefs().edit().clear()
            originalTokens.forEach { (k, v) -> when (v) {
                is String -> e.putString(k, v)
                is Long -> e.putLong(k, v)
                is Int -> e.putInt(k, v)
                is Boolean -> e.putBoolean(k, v)
            } }
            e.commit()
            c.prefs().edit().putString(C.HELIX_CLIENT_ID, originalClient).putStringSet(BlockedTags.KEY, originalTags).commit()
        }
    }
}
