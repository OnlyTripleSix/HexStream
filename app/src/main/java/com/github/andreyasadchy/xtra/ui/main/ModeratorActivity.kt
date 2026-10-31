package com.github.andreyasadchy.xtra.ui.main

import android.os.Bundle
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.repository.*
import com.github.andreyasadchy.xtra.util.*
import kotlinx.coroutines.Job
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

class ModeratorActivity : ChannelActionActivity() {
    private var channelId = ""
    private var channelLogin = ""
    private var queueJob: Job? = null
    private var queueStatusView: android.widget.TextView? = null
    private val held = linkedMapOf<String, JsonObject>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!prefs().getBoolean("hex_moderator_tools", true)) { clear("Moderator tools are hidden in Settings → Chat"); return }
        channelId = savedInstanceState?.getString("channel_id") ?: intent.getStringExtra("channel_id").orEmpty()
        channelLogin = savedInstanceState?.getString("channel_login") ?: intent.getStringExtra("channel_login").orEmpty()
        if (channelId.isNotBlank()) home() else channels()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("channel_id", channelId); outState.putString("channel_login", channelLogin); super.onSaveInstanceState(outState) }
    override fun onStop() {
        queueJob?.cancel(); queueJob = null
        queueStatusView?.text = getString(R.string.hex_tools_queue_paused)
        super.onStop()
    }
    private fun channels() {
        queueJob?.cancel(); loadedPages.clear(); loadedItems.clear(); clear("Your moderator channels")
        label("Choose a channel. Permissions belong to the active account and are checked by Twitch for every action.")
        val login = input("Channel username")
        button("Open channel tools") { task { session -> val user = session.user(login.text.toString().trim()); channelId = user.text("id"); channelLogin = user.text("login"); home() } }
        button("Open Twitch Mod View in browser") {
            val name = login.text.toString().trim()
            if (name.matches(Regex("[A-Za-z0-9_]{1,25}"))) openTwitch("https://www.twitch.tv/moderator/$name")
            else notice.text = getString(R.string.hex_tools_channel_hint)
        }
        button("Load channels I moderate") { loadChannels() }
    }
    private val loadedPages = mutableSetOf<String>()
    private val loadedItems = mutableSetOf<String>()
    private fun loadChannels(after: String? = null) {
        task { session ->
            if ("channels:$after" in loadedPages) return@task
            val result = session.moderated(after)
            loadedPages += "channels:$after"
            if (after == null && result.rows().isEmpty()) label("No moderator channels returned. You can enter a channel above, including your own.")
            result.rows().forEach { row -> val item = row.jsonObject
                if (loadedItems.add(item.text("broadcaster_id"))) button(item.text("broadcaster_name")) {
                    channelId = item.text("broadcaster_id"); channelLogin = item.text("broadcaster_login"); home()
                }
            }
            result.cursor()?.takeUnless { "channels:$it" in loadedPages }?.let { next -> button("Load more channels") { loadChannels(next) } }
        }
    }
    private fun home() {
        queueJob?.cancel(); clear("Moderator tools • ${channelLogin.ifBlank { channelId }}")
        label("Native tools use your active HexStream account. Destructive actions ask first.")
        button("People • ban, timeout, unban, warn") { people() }
        intent.getStringExtra("message_id")?.takeIf { it.isNotBlank() }?.let { id ->
            button("Delete selected chat message") { confirm("Delete this message?", "This removes the selected message from this channel's chat.") { task(true) { it.deleteMessages(channelId, id) } } }
        }
        button("Chat modes and announcements") { chat() }
        button("AutoMod • live queue, level and blocked terms") { automod() }
        button("Unban requests") { unbans() }
        button("Shield mode") { shield() }
        label("Full Twitch tools", 20f)
        label("Polls, predictions and the complete moderator workspace open Twitch in your browser. Twitch reserves some API actions for broadcasters. The browser uses its own Twitch login: check the account shown there.")
        button("Open Twitch Mod View • polls, predictions and more") { browserModView() }
        button("Polls • open Twitch poll manager") { browserModView(true) }
        button("Switch channel") { channels() }
    }
    private fun browserModView(poll: Boolean = false) {
        if (channelLogin.matches(Regex("[A-Za-z0-9_]{1,25}"))) openBrowserPanel(poll)
        else task { session -> channelLogin = session.userById(channelId).text("login"); openBrowserPanel(poll) }
    }
    private fun openBrowserPanel(poll: Boolean) {
        require(channelLogin.matches(Regex("[A-Za-z0-9_]{1,25}"))) { "Open a channel by username first." }
        openTwitch(if (poll) "https://www.twitch.tv/popout/$channelLogin/poll" else "https://www.twitch.tv/moderator/$channelLogin")
    }
    private fun people() {
        clear("People • $channelLogin")
        val target = input("Twitch username", intent.getStringExtra("target_login").orEmpty())
        val reason = input("Reason • optional for a ban/timeout, required for a warning", multiline = true)
        val seconds = input("Timeout in seconds • 600 = 10 minutes", "600", number = true)
        listOf("Timeout", "Ban", "Unban / remove timeout", "Warn").forEach { kind -> button(kind) {
            personAction(kind, target.text.toString().trim(), reason.text.toString().trim(), seconds.text.toString().toIntOrNull())
        } }
        button("Back to channel tools") { home() }
    }
    private fun personAction(kind: String, username: String, text: String, duration: Int?) {
        confirm("$kind $username?", "Channel: $channelLogin\n${if (kind == "Timeout") "Duration: $duration seconds\n" else ""}Reason: ${text.ifBlank { "None" }}") {
            task(true) { session ->
                val id = session.user(username).text("id")
                when (kind) {
                    "Ban" -> session.ban(channelId, id, text)
                    "Timeout" -> session.ban(channelId, id, text, duration ?: throw IllegalArgumentException("Enter a timeout of 1–1209600 seconds."))
                    "Unban / remove timeout" -> session.unban(channelId, id)
                    "Warn" -> session.warn(channelId, id, text)
                }
            }
        }
    }
    private fun chat() {
        clear("Chat controls • $channelLogin")
        val current = label("Load current modes to see what Twitch has enabled.")
        button("Refresh current modes") { refreshChat(current) }
        val slow = input("Slow mode seconds • 3–120", "10", number = true)
        val age = input("Follower age in minutes • 0 lets new followers chat", "0", number = true)
        listOf("slow_mode" to "Slow mode", "follower_mode" to "Followers only", "subscriber_mode" to "Subscribers only", "emote_mode" to "Emotes only", "unique_chat_mode" to "Unique chat").forEach { (key, title) ->
            button("$title • change") {
                getAlertDialogBuilder().setTitle(title).setItems(arrayOf("Enable", "Disable")) { _, which ->
                    val duration = when (key) { "slow_mode" -> slow.text.toString().toIntOrNull(); "follower_mode" -> age.text.toString().toIntOrNull(); else -> null }
                    confirm("${if (which == 0) "Enable" else "Disable"} $title?", "This changes $channelLogin's chat settings.") { task(true) { it.chatSetting(channelId, key, which == 0, duration) } }
                }.show()
            }
        }
        val announcement = input("Announcement • up to 500 characters", multiline = true)
        button("Send announcement") { val text = announcement.text.toString(); confirm("Send announcement?", text) { task(true) { it.announcement(channelId, text) } } }
        button("Clear entire chat") { confirm("Clear all chat messages?", "This clears the channel's chat for everyone. It cannot be undone.") { task(true) { it.deleteMessages(channelId) } } }
        button("Back to channel tools") { home() }
    }
    private fun refreshChat(view: android.widget.TextView) {
        task { session ->
            val settings = session.chatSettings(channelId).rows().firstOrNull()?.jsonObject ?: buildJsonObject {}
            view.text = listOf("slow_mode", "follower_mode", "subscriber_mode", "emote_mode", "unique_chat_mode").joinToString("\n") { "${it.replace('_', ' ')}: ${settings[it]?.jsonPrimitive?.booleanOrNull ?: false}" }
        }
    }
    private fun shield() {
        clear("Shield mode • $channelLogin")
        label("Shield mode applies the broadcaster's configured safety settings. Confirm the current state in Twitch Mod View if needed.")
        listOf(true, false).forEach { enabled -> button(if (enabled) "Activate Shield mode" else "Deactivate Shield mode") {
            confirm("${if (enabled) "Activate" else "Deactivate"} Shield mode?", "Channel: $channelLogin") { task(true) { it.shield(channelId, enabled) } }
        } }
        button("Back to channel tools") { home() }
    }
    private fun automod() {
        queueJob?.cancel(); clear("AutoMod • $channelLogin")
        label("The native queue receives new held messages while this page is listening. Use Twitch Mod View for messages held before connecting, private-term context, or gaps while disconnected.")
        val queueStatus = label("Live queue paused")
        queueStatusView = queueStatus
        val queue = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }; body.addView(queue)
        button("Listen for new held messages") {
            val session = api ?: return@button
            queueJob?.cancel(); held.clear(); renderHeld(queue)
            queueJob = lifecycleScope.launch {
                val condition = buildJsonObject { put("broadcaster_user_id", channelId); put("moderator_user_id", session.userId) }
                try { TwitchEvents.run(session, mapOf("automod.message.hold" to condition, "automod.message.update" to condition), { queueStatus.text = it }) { type, event ->
                    val id = event.text("message_id")
                    if (type == "automod.message.hold") { held[id] = event; while (held.size > 100) held.remove(held.keys.first()) } else held.remove(id)
                    renderHeld(queue)
                } } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { queueStatus.text = getString(R.string.hex_tools_queue_failed) }
            }
        }
        button("Open full AutoMod queue in Twitch Mod View") { browserModView() }
        button("View AutoMod settings") { task { session ->
            val data = session.automodSettings(channelId).rows().firstOrNull()?.jsonObject
            label(data?.entries?.filterNot { it.key.endsWith("_id") }?.joinToString("\n") { "${it.key.replace('_', ' ')}: ${it.value}" } ?: "No settings returned")
        } }
        button("Set AutoMod overall level") {
            getAlertDialogBuilder().setTitle("AutoMod level").setItems(arrayOf("0 • off", "1 • low", "2 • medium", "3 • high", "4 • maximum")) { _, level ->
                confirm("Set AutoMod to $level?", "This replaces the channel's custom category levels with the overall level.") { task(true) { it.automodLevel(channelId, level) } }
            }.show()
        }
        button("Blocked terms") { queueJob?.cancel(); terms() }
        button("Back to channel tools") { home() }
    }
    private fun renderHeld(queue: android.widget.LinearLayout) {
        for (index in queue.childCount - 1 downTo 0) {
            if (queue.getChildAt(index).tag !in held.keys) queue.removeViewAt(index)
        }
        val displayed = (0 until queue.childCount).map { queue.getChildAt(it).tag }.toSet()
        held.forEach { (id, event) ->
            if (id in displayed) return@forEach
            val card = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; tag = id }
            queue.addView(card)
            val text = event.heldMessageText()
            card.addView(android.widget.TextView(this).apply { this.text = getString(R.string.hex_tools_held_message, event.text("user_name"), text); setPadding(dp(4), dp(12), dp(4), dp(8)) })
            listOf(true, false).forEach { allow -> card.addView(android.widget.Button(this).apply {
                this.text = if (allow) "Allow message" else "Deny message"; isAllCaps = false
                setOnClickListener { if (!busy) confirm(if (allow) "Allow this message?" else "Deny this message?", text) { task(true) { it.automodMessage(id, allow); held.remove(id); renderHeld(queue) } } }
            }) }
        }
    }
    private fun terms() {
        loadedPages.clear(); loadedItems.clear(); clear("Blocked terms • $channelLogin")
        val term = input("Term to block • 2–500 characters")
        button("Add blocked term") { val text = term.text.toString(); confirm("Block this term?", text) { task(true) { it.addTerm(channelId, text); term.text.clear() } } }
        button("Load blocked terms") { loadTerms() }
        button("Refresh term list") { terms() }
        button("Back to AutoMod") { automod() }
    }
    private fun loadTerms(after: String? = null) {
        task { session ->
            if ("terms:$after" in loadedPages) return@task
            val result = session.terms(channelId, after); loadedPages += "terms:$after"
            if (result.rows().isEmpty()) label("No blocked terms returned.")
            result.rows().forEach { item -> val row = item.jsonObject
                if (loadedItems.add(row.text("id"))) button("Remove: ${row.text("text")}") { confirm("Remove blocked term?", row.text("text")) { task(true) { it.removeTerm(channelId, row.text("id")) } } }
            }
            result.cursor()?.takeUnless { "terms:$it" in loadedPages }?.let { cursor -> button("Load more terms") { loadTerms(cursor) } }
        }
    }
    private fun unbans() {
        loadedPages.clear(); loadedItems.clear(); clear("Unban requests • $channelLogin")
        val resolution = input("Resolution note • optional, up to 500 characters", multiline = true)
        button("Load pending requests") { loadUnbans(resolution) }
        button("Refresh request list") { unbans() }
        button("Back to channel tools") { home() }
    }
    private fun loadUnbans(resolution: android.widget.EditText, after: String? = null) {
        task { session ->
            if ("unbans:$after" in loadedPages) return@task
            val result = session.unbanRequests(channelId, after); loadedPages += "unbans:$after"
            if (result.rows().isEmpty()) label("No pending requests returned.")
            result.rows().forEach { item -> val row = item.jsonObject
                if (loadedItems.add(row.text("id"))) {
                    label("${row.text("user_name")}\n${row.text("text")}", 18f)
                    listOf(true, false).forEach { approve -> button(if (approve) "Approve ${row.text("user_name")}" else "Deny ${row.text("user_name")}") {
                        val note = resolution.text.toString()
                        confirm(if (approve) "Approve this unban request?" else "Deny this unban request?", "${row.text("user_name")}\n$note") { task(true) { it.resolveUnban(channelId, row.text("id"), approve, note) } }
                    } }
                }
            }
            result.cursor()?.takeUnless { "unbans:$it" in loadedPages }?.let { next -> button("Load more requests") { loadUnbans(resolution, next) } }
        }
    }
}
