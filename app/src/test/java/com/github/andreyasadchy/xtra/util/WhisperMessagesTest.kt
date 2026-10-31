package com.github.andreyasadchy.xtra.util

import org.junit.Assert.*
import org.junit.Test

class WhisperMessagesTest {
    private fun entry(id: String, time: Long = 10_000, from: String = "11", to: String = "22", body: String = "Hello") = WhisperEntry(id, from, "Sender", to, "Recipient", body, time)
    @Test fun repeatedPageDoesNotRepeatMessages() {
        val message = entry("one")
        assertEquals(listOf(message), WhisperMessages.merge("11", listOf(message), listOf(message, message)))
    }
    @Test fun identicalRepeatedSendsReconcileOneToOne() {
        val result = WhisperMessages.reconcile("11", listOf(entry("local-a"), entry("local-b", 10_100)), listOf(entry("server-a", 10_010), entry("server-b", 10_110)))
        assertEquals(listOf("server-a", "server-b"), result.entries.map { it.id }); assertEquals(setOf("local-a", "local-b"), result.retired)
    }
    @Test fun existingCanonicalMessageDoesNotConsumeANewIdenticalSend() {
        val server = entry("server")
        val pending = entry("local-new", 11_000)
        assertEquals(2, WhisperMessages.merge("11", listOf(server, pending), listOf(server)).size)
    }
    @Test fun otherSendersOrRecipientsCannotBeReconciledWithMyPendingSend() {
        assertEquals(3, WhisperMessages.merge("11", listOf(entry("local-a")), listOf(entry("incoming", from = "22", to = "11"), entry("different-peer", to = "33"))).size)
    }
    @Test fun messagesOutsideTheSubmissionWindowStayDistinct() {
        assertEquals(2, WhisperMessages.merge("11", listOf(entry("local-a", 1)), listOf(entry("server", 200_000))).size)
    }
    @Test fun historyFromAnotherAccountIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { WhisperMessages.merge("11", emptyList(), listOf(entry("foreign", from = "33", to = "44"))) }
    }
    @Test fun olderPagesSortWithoutOverwritingOrDuplicatingRecentMessages() {
        val result = WhisperMessages.merge("11", listOf(entry("new", 30_000)), listOf(entry("middle", 20_000), entry("old", 1_000)))
        assertEquals(listOf("old", "middle", "new"), result.map { it.id })
    }
    @Test fun retiredLocalAndDeletedRemoteIdsCannotReappearFromAnOldSnapshot() {
        val retired = setOf("local-a", "deleted")
        val result = WhisperMessages.merge("11", listOf(entry("canonical"), entry("deleted")), listOf(entry("local-a")), retired)
        assertEquals(listOf("canonical"), result.map { it.id })
    }
}
