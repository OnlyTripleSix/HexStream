package com.github.andreyasadchy.xtra.util

import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.os.Bundle
import com.github.andreyasadchy.xtra.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Only the visible app's active account connects. No whisper worker or background service. */
object WhisperCenter {
    const val ENABLED = "hex_whispers"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()
    private val mutableEntries = MutableStateFlow<List<WhisperEntry>>(emptyList())
    val entries = mutableEntries.asStateFlow()
    val status = MutableStateFlow("Whispers are off")
    private var account: String? = null
    private val retired = linkedSetOf<String>()
    internal fun retiredIds(owner: String): Set<String> = if (account == owner) retired.toSet() else emptySet()
    private var connection: Job? = null
    private var delayedStop: Job? = null
    private var started = 0
    private lateinit var app: Application
    private val preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (started > 0 && (key == null || key in setOf(ENABLED, C.USER_ID, C.TOKEN, C.HELIX_CLIENT_ID))) restart()
    }

    fun initialize(application: Application) {
        if (::app.isInitialized) return
        app = application
        app.prefs().registerOnSharedPreferenceChangeListener(preferencesListener)
        app.tokenPrefs().registerOnSharedPreferenceChangeListener(preferencesListener)
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { started++; delayedStop?.cancel(); if (connection == null) restart() }
            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) delayedStop = scope.launch { delay(700); if (started == 0) { connection?.cancel(); connection = null; status.value = "Paused while HexStream is in the background" } }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
    private fun restart() {
        connection?.cancel(); connection = null
        val newAccount = app.tokenPrefs().getString(C.USER_ID, null)
        if (newAccount != account) { mutableEntries.value = emptyList(); retired.clear(); account = newAccount }
        if (!app.prefs().getBoolean(ENABLED, false)) { status.value = "Whispers are off"; return }
        connection = scope.launch {
            try {
                val api = ChannelActionSession.open(app)
                status.value = "Connecting…"
                lock.withLock {
                    val loaded = withContext(Dispatchers.IO) { WhisperHistory.read(app, api.userId) }
                    api.checkCurrent(); mutableEntries.value = loaded
                }
                TwitchEvents.run(api, mapOf("user.whisper.message" to buildJsonObject { put("user_id", api.userId) }), { status.value = it }) { _, event ->
                    val from = event.text("from_user_id"); val to = event.text("to_user_id")
                    if (api.userId == from || api.userId == to) append(api, WhisperEntry(event.text("whisper_id"), from, event.text("from_user_name"), to, event.text("to_user_name"), event["whisper"]!!.jsonObject.text("text"), System.currentTimeMillis()))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: ActionFailure) { status.value = e.message.orEmpty() }
            catch (_: Exception) { status.value = "Could not open encrypted whisper history or connect. Reopen Whispers to retry; clear local history only if needed." }
        }
    }
    fun reconnect() { if (started > 0) restart() }
    suspend fun append(api: ChannelActionsApi, entry: WhisperEntry) = lock.withLock {
        api.checkCurrent()
        if (account != api.userId) return@withLock
        if (entry.id in retired) return@withLock
        val next = WhisperMessages.merge(api.userId, mutableEntries.value, listOf(entry)).takeLast(500)
        withContext(Dispatchers.IO) { WhisperHistory.write(app, api.userId, next) }
        api.checkCurrent(); mutableEntries.value = next
    }
    suspend fun clear(api: ChannelActionsApi) = lock.withLock {
        api.checkCurrent()
        withContext(Dispatchers.IO) { WhisperHistory.clear(app, api.userId) }
        api.checkCurrent(); mutableEntries.value = emptyList()
    }
    suspend fun clearActiveHistory(owner: String) = lock.withLock {
        if (owner.isBlank() || app.tokenPrefs().getString(C.USER_ID, null) != owner) throw ActionFailure("The active account changed. Reopen Whispers.")
        withContext(Dispatchers.IO) { WhisperHistory.clear(app, owner) }
        if (app.tokenPrefs().getString(C.USER_ID, null) != owner) throw ActionFailure("The active account changed. Reopen Whispers.")
        mutableEntries.value = emptyList()
    }
    suspend fun importHistory(api: WhisperHistoryApi, entries: List<WhisperEntry>, deleted: Set<String>) = lock.withLock {
        api.checkCurrent()
        if (account != api.accountId) throw ActionFailure("The active account changed. Reopen Whispers.")
        val result = WhisperMessages.reconcile(api.accountId, mutableEntries.value.filterNot { it.id in retired }, entries.filterNot { it.id in retired }, deleted)
        val merged = result.entries.takeLast(500)
        withContext(Dispatchers.IO) { WhisperHistory.write(app, api.accountId, merged) }
        api.checkCurrent()
        retired += result.retired
        while (retired.size > 2_000) retired.remove(retired.first())
        mutableEntries.value = merged
    }
}
