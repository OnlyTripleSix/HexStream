package com.github.andreyasadchy.xtra.ui.main

import android.os.Bundle
import com.github.andreyasadchy.xtra.R
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.andreyasadchy.xtra.repository.*
import com.github.andreyasadchy.xtra.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.text.DateFormat
import java.util.Date
import java.util.UUID

class WhispersActivity : ChannelActionActivity() {
    private var render: () -> Unit = {}
    private var connectionStatusJob: Job? = null
    private var selectedId = ""
    private var selectedName = ""
    private var draftView: android.widget.EditText? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!prefs().getBoolean(WhisperCenter.ENABLED, false)) { clear("Whispers are off"); label("Enable Whispers in Settings → Chat to show the menu and receive new messages while HexStream is open."); return }
        conversations()
        val restoredId = savedInstanceState?.getString("whisper_target").orEmpty()
        if (restoredId.isNotBlank()) conversation(restoredId, savedInstanceState?.getString("whisper_name").orEmpty(), savedInstanceState?.getString("whisper_draft").orEmpty())
        else intent.getStringExtra("target_login")?.takeIf { it.isNotBlank() }?.let { login -> task { session -> val user = session.user(login); conversation(user.text("id"), user.text("login")) } }
        if (savedInstanceState?.getBoolean("whisper_submitting") == true) notice.text = getString(R.string.hex_tools_uncertain)
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { WhisperCenter.entries.collect { render() } } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("whisper_target", selectedId); outState.putString("whisper_name", selectedName)
        outState.putString("whisper_draft", draftView?.text?.toString())
        outState.putBoolean("whisper_submitting", busy)
        super.onSaveInstanceState(outState)
    }
    private fun connectionLabel(): TextView {
        val view = label(WhisperCenter.status.value)
        connectionStatusJob?.cancel()
        connectionStatusJob = lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { WhisperCenter.status.collect { view.text = it } } }
        return view
    }
    private fun conversations() {
        selectedId = ""; selectedName = ""; draftView = null
        clear("Whispers")
        label("New messages arrive while HexStream is open. Twitch does not provide old conversation history or messages missed while disconnected. This encrypted local history keeps the most recent 500 messages for this account.")
        connectionLabel()
        button("Reconnect whispers") { WhisperCenter.reconnect() }
        val login = input("Twitch username")
        button("New conversation") { task { session -> val user = session.user(login.text.toString().trim()); conversation(user.text("id"), user.text("login")) } }
        button("Clear this account's local history") { confirm("Clear local whisper history?", "This removes only this account's history stored by HexStream. It does not delete Twitch messages.") { task { WhisperCenter.clear(it) } } }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(list)
        render = {
            list.removeAllViews()
            val active = api?.userId
            WhisperCenter.entries.value.asReversed().distinctBy { if (it.fromId == active) it.toId else it.fromId }.take(100).forEach { entry ->
                val target = if (entry.fromId == active) entry.toId else entry.fromId
                val name = if (entry.fromId == active) entry.to else entry.from
                list.addView(android.widget.Button(this).apply {
                    text = getString(R.string.hex_tools_conversation_preview, name, entry.text.take(100)); isAllCaps = false; minHeight = dp(56)
                    setOnClickListener { if (!busy) conversation(target, name) }
                })
            }
        }
        render()
    }
    private fun conversation(id: String, name: String, restoredDraft: String = "") {
        selectedId = id; selectedName = name
        clear("Whisper • $name")
        connectionLabel()
        label("Twitch may restrict whispers to verified accounts or reject messages because of recipient privacy settings. An accepted send is not a delivery receipt.")
        button("All conversations") { conversations() }
        val messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(messages)
        label("Showing the latest 100 messages in this conversation.")
        val draft = input("Message • up to 500 characters", value = restoredDraft, multiline = true)
        draftView = draft
        val sendStatus = label("")
        button("Send whisper") {
            val text = draft.text.toString()
            confirm("Send whisper to $name?", text) {
                task(true) { session ->
                    session.whisper(id, text)
                    draft.text.clear()
                    sendStatus.text = getString(R.string.hex_tools_whisper_submitted)
                    WhisperCenter.append(session, WhisperEntry("local-${UUID.randomUUID()}", session.userId, tokenPrefs().getString(C.USERNAME, "You").orEmpty(), id, name, text, System.currentTimeMillis()))
                    delay(2_000)
                }
            }
        }
        render = {
            messages.removeAllViews()
            val relevant = WhisperCenter.entries.value.filter { it.fromId == id || it.toId == id }.takeLast(100)
            relevant.forEach { entry -> messages.addView(TextView(this).apply {
                text = getString(R.string.hex_tools_whisper_entry, entry.from, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.time)), entry.text)
                setTextIsSelectable(true); textSize = 16f; setPadding(dp(4), dp(12), dp(4), dp(12))
            }) }
        }
        render()
    }
}
