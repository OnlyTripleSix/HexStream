package com.github.andreyasadchy.xtra.ui.main

import android.content.Context
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.repository.*
import com.github.andreyasadchy.xtra.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

data class WhisperContact(val peer: WhisperPeer, val threadId: String?, val latest: WhisperEntry?, val archived: Boolean = false)
data class WhispersScreen(
    val accountId: String = "", val contacts: List<WhisperContact> = emptyList(), val selected: WhisperPeer? = null,
    val messages: List<WhisperEntry> = emptyList(), val loading: Boolean = false, val sending: Boolean = false,
    val moreThreads: Boolean = false, val moreMessages: Boolean = false, val historyLoaded: Boolean = false,
    val status: String = "Loading saved conversations…",
    val verificationRequired: Boolean = false,
)

/** History requests and drafts survive rotation. Never export or log message contents. */
class WhispersHistoryViewModel : ViewModel() {
    val state = MutableStateFlow(WhispersScreen())
    private lateinit var app: Application
    private var history: WhisperHistoryApi? = null
    private val threads = linkedMapOf<String, WhisperThread>()
    private var local = emptyList<WhisperEntry>()
    private val drafts = mutableMapOf<String, String>()
    private var nextThreads: String? = null
    private var nextMessages: String? = null
    private val threadCursors = mutableSetOf<String?>()
    private val messageCursors = mutableSetOf<String?>()
    private var readJob: Job? = null
    private var readGeneration = 0
    private var localJob: Job? = null
    private var selectedMessages = emptyList<WhisperEntry>()
    private var selectedThread: String? = null
    private var serverWindow = false
    private var oldestServerTime = Long.MAX_VALUE
    private var verificationRetry: (() -> Unit)? = null
    fun retryVerifiedHistory() { state.value = state.value.copy(verificationRequired = false); verificationRetry?.invoke() }

    fun attach(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext as Application
        val owner = app.tokenPrefs().getString(C.USER_ID, null).orEmpty()
        state.value = state.value.copy(accountId = owner)
        localJob = viewModelScope.launch {
            WhisperCenter.entries.collect { entries ->
                val previousIds = local.mapTo(mutableSetOf()) { it.id }
                local = entries.filter { it.fromId == owner || it.toId == owner }
                state.value.selected?.let { peer ->
                    val rows = local.filter { it.fromId == peer.id || it.toId == peer.id }
                    val visible = if (!serverWindow) rows.takeLast(20) else rows.filter { it.time >= oldestServerTime || (it.id.startsWith("local-") && it.id !in previousIds) }
                    selectedMessages = WhisperMessages.merge(owner, selectedMessages, visible, WhisperCenter.retiredIds(owner))
                }
                publish()
            }
        }
    }
    private fun session(): WhisperHistoryApi {
        if (state.value.accountId.isBlank()) throw ActionFailure(WhisperHistoryApi.LOGIN_REQUIRED)
        check(app.tokenPrefs().getString(C.USER_ID, null) == state.value.accountId) { "Active account changed" }
        return WhisperHistorySession.open(app).also { history = it }
    }
    private fun contacts(): List<WhisperContact> {
        val owner = state.value.accountId
        val result = threads.values.associateByTo(linkedMapOf()) { it.peer.id }.mapValuesTo(linkedMapOf()) { (_, thread) ->
            WhisperContact(thread.peer, thread.id, thread.messages.maxByOrNull { it.time }, thread.archived)
        }
        local.forEach { entry ->
            val id = if (entry.fromId == owner) entry.toId else entry.fromId
            val name = if (entry.fromId == owner) entry.to else entry.from
            val previous = result[id]
            if (previous == null) result[id] = WhisperContact(WhisperPeer(id, "", name), null, entry)
            else if (previous.latest == null || entry.time > previous.latest.time) result[id] = previous.copy(latest = entry)
        }
        return result.values.sortedWith(compareByDescending<WhisperContact> { it.latest?.time ?: 0 }.thenBy { it.peer.id })
    }
    private fun publish() {
        if (::app.isInitialized && app.tokenPrefs().getString(C.USER_ID, null).orEmpty() != state.value.accountId) {
            readGeneration++; readJob?.cancel()
            threads.clear(); local = emptyList(); selectedMessages = emptyList(); selectedThread = null
            nextThreads = null; nextMessages = null; drafts.clear()
            state.value = WhispersScreen(accountId = state.value.accountId, status = "The active account changed. Reopen Whispers.")
            return
        }
        state.value = state.value.copy(contacts = contacts(), messages = selectedMessages, moreThreads = nextThreads != null,
            moreMessages = state.value.selected != null && selectedThread != null && nextMessages != null)
    }
    private fun read(block: suspend (WhisperHistoryApi) -> Unit): Job? {
        if (state.value.loading) return null
        val generation = ++readGeneration
        state.value = state.value.copy(loading = true, status = "Loading Twitch history…", verificationRequired = false)
        readJob = viewModelScope.launch {
            try { val api = session(); block(api); api.checkCurrent() }
            catch (e: CancellationException) { throw e }
            catch (e: ActionFailure) { if (generation == readGeneration) state.value = state.value.copy(status = e.message.orEmpty(), verificationRequired = e.message == WhisperHistoryApi.VERIFICATION_REQUIRED) }
            catch (e: IllegalArgumentException) { if (generation == readGeneration) state.value = state.value.copy(status = "Twitch history could not be verified. Your saved messages are kept.") }
            catch (_: Exception) { if (generation == readGeneration) state.value = state.value.copy(status = "Could not load Twitch history. Your saved messages are kept; tap Refresh to retry.") }
            finally { if (generation == readGeneration) { state.value = state.value.copy(loading = false); publish() } }
        }
        return readJob
    }
    private suspend fun remember(api: WhisperHistoryApi, thread: WhisperThread) {
        api.checkCurrent()
        WhisperCenter.importHistory(api, thread.messages, thread.deleted)
        api.checkCurrent()
        val previous = threads[thread.id]
        threads[thread.id] = if (previous == null) thread else thread.copy(messages = WhisperMessages.merge(api.accountId, previous.messages, thread.messages, thread.deleted).takeLast(20))
    }
    fun refresh() {
        if (state.value.selected == null) loadThreads(true) else loadMessages(true)
    }
    fun loadThreads(reset: Boolean = false) {
        verificationRetry = { loadThreads(reset) }
        read { api ->
            if (reset) { threadCursors.clear(); nextThreads = null }
            var cursor = if (reset) null else nextThreads
            if (!reset && cursor == null) return@read
            if (cursor in threadCursors) throw ActionFailure("Twitch repeated a conversation page. Tap Refresh to retry.")
            val page = api.threads(cursor)
            threadCursors += cursor
            page.threads.forEach { remember(api, it) }
            nextThreads = page.next?.takeUnless { it in threadCursors }
            if (page.next != null && nextThreads == null) throw ActionFailure("Twitch repeated a conversation cursor. Saved conversations are kept; tap Refresh to retry.")
            state.value = state.value.copy(historyLoaded = true, status = when {
                page.unavailable > 0 -> "${threads.size} conversations loaded • ${page.unavailable} unavailable conversations skipped"
                page.threads.isEmpty() && threads.isEmpty() -> "Twitch returned no conversations for this account."
                else -> "${threads.size} conversations loaded from Twitch"
            })
        }
    }
    fun select(peer: WhisperPeer) {
        if (state.value.sending) return
        readGeneration++; readJob?.cancel()
        selectedThread = threads.values.firstOrNull { it.peer.id == peer.id }?.id
        serverWindow = false; oldestServerTime = Long.MAX_VALUE
        selectedMessages = local.filter { it.fromId == peer.id || it.toId == peer.id }.takeLast(20)
        threads[selectedThread]?.let { selectedMessages = WhisperMessages.merge(state.value.accountId, selectedMessages, it.messages, it.deleted) }
        nextMessages = null; messageCursors.clear()
        state.value = state.value.copy(selected = peer, loading = false, status = "Loading conversation…")
        publish(); loadMessages(true)
    }
    fun inbox() {
        if (state.value.sending) return
        readGeneration++; readJob?.cancel(); selectedMessages = emptyList(); selectedThread = null; nextMessages = null
        state.value = state.value.copy(selected = null, loading = false, status = "Conversations")
        publish(); loadThreads(true)
    }
    fun find(login: String) {
        if (state.value.loading || state.value.sending) return
        var found: WhisperPeer? = null
        val generation = ++readGeneration
        state.value = state.value.copy(loading = true, status = "Finding Twitch user…")
        val job = viewModelScope.launch {
            try {
                val peer = try { session().user(login.trim()) }
                catch (_: ActionFailure) {
                    val api = ChannelActionSession.open(app)
                    if (api.userId != state.value.accountId) throw ActionFailure("The active account changed. Reopen Whispers.")
                    val user = api.user(login.trim())
                    WhisperPeer(user.text("id"), user.text("login"), user.text("display_name"))
                }
                if (peer.id == state.value.accountId) throw ActionFailure("Choose another Twitch user.")
                found = peer
            } catch (e: CancellationException) { throw e }
            catch (e: ActionFailure) { if (generation == readGeneration) state.value = state.value.copy(status = e.message.orEmpty()) }
            catch (_: Exception) { if (generation == readGeneration) state.value = state.value.copy(status = "Could not find that Twitch user. Check the username and login, then retry.") }
            finally { if (generation == readGeneration) { state.value = state.value.copy(loading = false); publish() } }
        }
        readJob = job
        job.invokeOnCompletion { cause -> if (cause == null && generation == readGeneration) found?.let { peer -> viewModelScope.launch { select(peer) } } }
    }
    private suspend fun findThread(api: WhisperHistoryApi, peer: WhisperPeer): WhisperThread? {
        threads.values.firstOrNull { it.peer.id == peer.id }?.let { return it }
        val seen = mutableSetOf<String?>()
        var cursor: String? = null
        repeat(10) {
            if (!seen.add(cursor)) throw ActionFailure("Twitch repeated a history page. Tap Refresh to retry.")
            val page = api.threads(cursor)
            page.threads.forEach { remember(api, it) }
            page.threads.firstOrNull { it.peer.id == peer.id }?.let { return it }
            cursor = page.next ?: return null
        }
        throw ActionFailure("This conversation is farther back. Load more conversations from the inbox, then select it.")
    }
    fun loadMessages(reset: Boolean = false) {
        val peer = state.value.selected ?: return
        verificationRetry = { loadMessages(reset) }
        read { api ->
            if (selectedThread == null) selectedThread = findThread(api, peer)?.id
            val id = selectedThread
            if (id == null) {
                nextMessages = null
                state.value = state.value.copy(status = "Twitch returned no saved conversation with this user. You can start one below.")
                return@read
            }
            val previousLocalIds = local.mapTo(mutableSetOf()) { it.id }
            val cursor = if (reset) null else nextMessages
            if (!reset && cursor == null) return@read
            if (!reset && cursor in messageCursors) throw ActionFailure("Twitch repeated a message page. Tap Refresh to retry.")
            val thread = api.thread(id, peer.id, cursor)
            api.checkCurrent()
            if (state.value.selected?.id != peer.id) return@read
            remember(api, thread)
            val deleted = thread.deleted + WhisperCenter.retiredIds(state.value.accountId)
            var acceptCursor = true
            if (reset) {
                val during = local.filter { (it.fromId == peer.id || it.toId == peer.id) && it.id !in previousLocalIds }
                val refreshed = WhisperHistoryWindow.newest(state.value.accountId, selectedMessages, thread.messages, during, serverWindow, oldestServerTime, deleted)
                selectedMessages = refreshed.messages; oldestServerTime = refreshed.oldestServerTime
                if (refreshed.replaced) messageCursors.clear() else acceptCursor = false
            } else {
                oldestServerTime = minOf(oldestServerTime, thread.messages.minOfOrNull { it.time } ?: Long.MAX_VALUE)
                val pending = local.filter { (it.fromId == peer.id || it.toId == peer.id) && it.id.startsWith("local-") && it.time >= oldestServerTime }
                selectedMessages = WhisperMessages.merge(state.value.accountId, selectedMessages + pending, thread.messages, deleted)
            }
            serverWindow = true
            if (acceptCursor) {
                messageCursors += cursor
                nextMessages = thread.next?.takeUnless { it in messageCursors }
                if (thread.next != null && nextMessages == null) throw ActionFailure("Twitch repeated a message cursor. Saved messages are kept; tap Refresh to retry.")
            }
            state.value = state.value.copy(selected = thread.peer, status = when {
                selectedMessages.isEmpty() && nextMessages != null -> "Older message pages are available. Tap Load older messages to continue."
                selectedMessages.isEmpty() -> "No saved messages in this conversation."
                nextMessages != null -> "${selectedMessages.size} messages loaded • scroll up for older messages"
                else -> "${selectedMessages.size} messages loaded"
            })
        }
    }
    fun draft(): String = state.value.selected?.id?.let { drafts[it] }.orEmpty()
    fun draft(value: String) { state.value.selected?.id?.let { drafts[it] = value } }
    fun send(text: String) {
        val peer = state.value.selected ?: return
        if (state.value.sending) return
        state.value = state.value.copy(sending = true, status = "Submitting to Twitch…")
        viewModelScope.launch {
            try {
                val api = ChannelActionSession.open(app)
                if (api.userId != state.value.accountId) throw ActionFailure("The active account changed. Reopen Whispers.")
                api.whisper(peer.id, text)
                if (drafts[peer.id] == text) drafts[peer.id] = ""
                val entry = WhisperEntry("local-${UUID.randomUUID()}", api.userId, app.tokenPrefs().getString(C.USERNAME, "You").orEmpty(), peer.id, peer.name, text, System.currentTimeMillis())
                WhisperCenter.append(api, entry)
                selectedMessages = WhisperMessages.merge(api.userId, selectedMessages, listOf(entry))
                state.value = state.value.copy(status = "Submitted to Twitch. Delivery is not confirmed."); publish()
                delay(2_000)
            } catch (e: CancellationException) { throw e }
            catch (e: ActionFailure) { state.value = state.value.copy(status = e.message.orEmpty()) }
            catch (e: IllegalArgumentException) { state.value = state.value.copy(status = "Enter a message of 1–500 characters.") }
            catch (_: Exception) { state.value = state.value.copy(status = "Sending was interrupted. Check Twitch before repeating the message; it may have succeeded.") }
            finally { state.value = state.value.copy(sending = false); publish() }
        }
    }
    fun clearLocal() {
        viewModelScope.launch {
            try { WhisperCenter.clearActiveHistory(state.value.accountId); state.value = state.value.copy(status = "Local cache cleared. Twitch history is unchanged.") }
            catch (_: Exception) { state.value = state.value.copy(status = "Could not clear the local cache. Reconnect your active account and retry.") }
        }
    }
}
