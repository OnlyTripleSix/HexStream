package com.github.andreyasadchy.xtra.util

/** Reconcile submitted local sends one-to-one with canonical server messages. */
internal object WhisperMessages {
    data class Reconciled(val entries: List<WhisperEntry>, val retired: Set<String>)
    fun merge(owner: String, existing: List<WhisperEntry>, incoming: List<WhisperEntry>, deleted: Set<String> = emptySet()) = reconcile(owner, existing, incoming, deleted).entries
    fun reconcile(owner: String, existing: List<WhisperEntry>, incoming: List<WhisperEntry>, deleted: Set<String> = emptySet()): Reconciled {
        require((existing + incoming).all { it.fromId == owner || it.toId == owner }) { "Messages belong to a different account." }
        val rows = existing.filterNot { it.id in deleted }.associateByTo(linkedMapOf()) { it.id }
        val retired = deleted.toMutableSet()
        incoming.filterNot { it.id in deleted }.forEach { item ->
            if (!rows.containsKey(item.id) && !item.id.startsWith("local-") && item.fromId == owner) {
                rows.values.filter { it.id.startsWith("local-") && it.fromId == item.fromId && it.toId == item.toId && it.text == item.text && kotlin.math.abs(it.time - item.time) <= 120_000 }
                    .minByOrNull { kotlin.math.abs(it.time - item.time) }?.let { rows.remove(it.id); retired += it.id }
            }
            rows[item.id] = item
        }
        return Reconciled(rows.values.sortedWith(compareBy<WhisperEntry> { it.time }.thenBy { it.id }), retired)
    }
}
