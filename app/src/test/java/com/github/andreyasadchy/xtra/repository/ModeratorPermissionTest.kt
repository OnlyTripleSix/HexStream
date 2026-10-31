package com.github.andreyasadchy.xtra.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ModeratorPermissionTest {
    @Test fun owningTheChannelNeedsNoMembershipRequest() = runBlocking {
        assertTrue(ModeratorPermission.check("11", "11") { error("Unnecessary lookup") })
    }
    @Test fun missingOrInvalidIdentityNeverGrantsAccess() = runBlocking {
        assertFalse(ModeratorPermission.check("", "") { error("Unexpected lookup") })
        assertFalse(ModeratorPermission.check("11", "name") { error("Unexpected lookup") })
    }
    @Test fun exactChannelIdentityIsRequired() = runBlocking {
        assertFalse(ModeratorPermission.check("11", "22") { ModeratorPermission.Page(setOf("222", "33"), null) })
    }
    @Test fun targetOnALaterPageIsRecognized() = runBlocking {
        val cursors = mutableListOf<String?>()
        assertTrue(ModeratorPermission.check("11", "22") { cursor ->
            cursors += cursor
            if (cursor == null) ModeratorPermission.Page(setOf("33"), "next") else ModeratorPermission.Page(setOf("22"), null)
        })
        assertEquals(listOf(null, "next"), cursors)
    }
    @Test fun emptyIntermediatePageDoesNotSkipAValidLaterRole() = runBlocking {
        assertTrue(ModeratorPermission.check("11", "22") { cursor ->
            if (cursor == null) ModeratorPermission.Page(emptySet(), "next") else ModeratorPermission.Page(setOf("22"), null)
        })
    }
    @Test fun repeatedCursorFailsClosed() {
        assertThrows(ActionFailure::class.java) { runBlocking { ModeratorPermission.check("11", "22") { ModeratorPermission.Page(emptySet(), "same") } } }
    }
    @Test fun roleLookupCancellationCannotGrantPermission() {
        assertThrows(CancellationException::class.java) { runBlocking { ModeratorPermission.check("11", "22") { throw CancellationException() } } }
    }
    @Test fun unboundedPaginationFailsClosed() {
        var calls = 0
        assertThrows(ActionFailure::class.java) { runBlocking { ModeratorPermission.check("11", "22") { ModeratorPermission.Page(emptySet(), (++calls).toString()) } } }
        assertEquals(100, calls)
    }
}
