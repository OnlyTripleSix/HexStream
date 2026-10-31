package com.github.andreyasadchy.xtra.util

import org.junit.Assert.*
import org.junit.Test

class WhisperHistoryHeadersTest {
    private val base = mapOf("Authorization" to "OAuth fixture", "Client-Id" to "fixture-client")
    private val proof = mapOf("authorization" to "OAuth fixture", "CLIENT-ID" to "fixture-client", "Client-Integrity" to "fixture-proof")
    @Test fun verifiedHeadersMatchCredentialsWithoutCaseSensitiveHeaderNames() {
        assertEquals("fixture-proof", WhisperHistoryHeaders.choose(base, proof, true)["Client-Integrity"])
    }
    @Test fun anotherLoginOrClientCannotSupplyVerificationHeaders() {
        assertEquals(base, WhisperHistoryHeaders.choose(base, proof + ("authorization" to "OAuth other"), true))
        assertEquals(base, WhisperHistoryHeaders.choose(base, proof + ("CLIENT-ID" to "other"), true))
    }
    @Test fun expiredOrMissingVerificationKeepsTheExistingLogin() {
        assertEquals(base, WhisperHistoryHeaders.choose(base, proof, false))
        assertEquals(base, WhisperHistoryHeaders.choose(base, proof - "Client-Integrity", true))
        assertTrue(WhisperHistoryHeaders.choose(emptyMap(), proof, true).isEmpty())
    }
}
