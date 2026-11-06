package com.github.andreyasadchy.xtra.ui.main

import android.os.Bundle
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.repository.*
import com.github.andreyasadchy.xtra.util.*
import kotlinx.coroutines.Job
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

class ChannelModeratorDialog : ChannelActionDialog() {
    private var channelId = ""
    private var channelLogin = ""
    private var queueJob: Job? = null
    private var queueStatusView: android.widget.TextView? = null
    private val held = linkedMapOf<String, JsonObject>()
    companion object {
        fun newInstance(channelId: String, channelLogin: String? = null, targetId: String? = null, targetLogin: String? = null, messageId: String? = null, quickAction: String? = null) = ChannelModeratorDialog().apply {
            arguments = Bundle().apply {
                putString("channel_id", channelId)
                putString("channel_login", channelLogin)
                putString("target_id", targetId)
                putString("target_login", targetLogin)
                putString("message_id", messageId)
                putString("quick_action", quickAction)
            }
        }
    }
    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.savedInstanceState = savedInstanceState
        if (!requireContext().prefs().getBoolean("hex_moderator_tools", true)) { clear("Moderator tools are hidden in Settings → Chat"); return }
        channelId = requireArguments().getString("channel_id") ?: savedInstanceState?.getString("channel_id").orEmpty()
        channelLogin = savedInstanceState?.getString("channel_login") ?: requireArguments().getString("channel_login").orEmpty()
        if (channelId.isNotBlank()) viewLifecycleOwner.lifecycleScope.launch {
            awaitIdle()
            verifyAndOpen(channelId)
        } else clear("Channel details are still loading. Close and reopen the controls.")
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("channel_id", channelId); outState.putString("channel_login", channelLogin); super.onSaveInstanceState(outState) }
    override fun onStop() {
        queueJob?.cancel(); queueJob = null
        queueStatusView?.text = getString(R.string.hex_tools_queue_paused)
        super.onStop()
    }
    private var savedInstanceState: Bundle? = null
    private fun verifyAndOpen(targetId: String) {
        val action = requireArguments().getString("quick_action")
        if (action != null && savedInstanceState == null && api != null) {
            savedInstanceState = Bundle()
            // The message panel already verified visibility. Confirm immediately, then recheck on submit.
            people()
            val username = requireArguments().getString("target_login").orEmpty()
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.lifecycle.withResumed {
                    when (action) {
                        "ban" -> personAction("Ban", username, "", null)
                        "timeout" -> chooseTimeout(username, "")
                        "delete" -> deleteSelectedMessage()
                    }
                }
            }
            return
        }
        clear("Channel controls")
        button("Retry access check") { verifyAndOpen(targetId) }
        button("Moderator setup") { ChannelActionSession.showModeratorSetup(requireContext(), "Check the active account and its Twitch permissions.") }
        task { session ->
            session.requireModerator(targetId)
            val channel = session.userById(targetId)
            check(channel.text("id") == targetId)
            channelId = targetId; channelLogin = channel.text("login")
            home()
        }
    }
    private val loadedPages = mutableSetOf<String>()
    private val loadedItems = mutableSetOf<String>()
    private fun home() {
        queueJob?.cancel(); clear("Moderator tools • ${channelLogin.ifBlank { channelId }}")
        label("Changes use the account shown above. You'll confirm before submitting.")
        button("Ban or timeout a user") { people() }
        button("Polls") { openPolls() }
        requireArguments().getString("message_id")?.takeIf { it.isNotBlank() }?.let { id ->
            button("Delete selected chat message") { confirm("Delete this message?", "This removes the selected message from this channel's chat.") { task(true) { it.deleteMessages(channelId, id) } } }
        }
        button("Chat modes and announcements") { chat() }
        button("AutoMod • live queue, level and blocked terms") { automod() }
        button("Unban requests") { unbans() }
        button("Shield mode") { shield() }
        button("More Twitch controls") { TwitchPollDialog.newInstance(channelId, channelLogin, true).show(childFragmentManager, "twitchControls") }
    }
    private fun openPolls() {
        task { session ->
            session.requireModerator(channelId)
            TwitchPollDialog.newInstance(channelId, channelLogin).show(childFragmentManager, "channelPolls")
        }
    }
    private fun deleteSelectedMessage() {
        val id = requireArguments().getString("message_id")?.takeIf { it.isNotBlank() } ?: return
        confirm("Delete this message?", "This removes only the selected message from $channelLogin's chat.", "Delete message") {
            task(true) { it.deleteMessages(channelId, id) }
        }
    }
    private fun people() {
        clear("Ban or timeout • $channelLogin")
        val selected = requireArguments().getString("target_login").orEmpty()
        val target = input("Twitch username", selected)
        val reason = input("Reason (optional)", multiline = true)
        button("Ban") { personAction("Ban", target.text.toString(), reason.text.toString(), null) }
        button("Timeout…") { chooseTimeout(target.text.toString(), reason.text.toString()) }
        if (!requireArguments().getString("message_id").isNullOrBlank()) button("Delete selected message") { deleteSelectedMessage() }
        button("Unban / remove timeout") { personAction("Unban", target.text.toString(), reason.text.toString(), null) }
        button("Warn (reason required)") { personAction("Warn", target.text.toString(), reason.text.toString(), null) }
        button("Channel controls") { home() }
    }
    private fun chooseTimeout(username: String, reason: String) {
        val durations = arrayOf("1 minute", "5 minutes", "10 minutes", "30 minutes", "1 hour", "1 day", "Custom duration…")
        val seconds = intArrayOf(60, 300, 600, 1800, 3600, 86400)
        val choice = requireContext().getAlertDialogBuilder().setTitle("Timeout $username")
            .setItems(durations) { _, which ->
                if (which < seconds.size) personAction("Timeout", username, reason, seconds[which])
                else {
                    val field = android.widget.EditText(requireContext()).apply {
                        hint = "30s, 10m, 2h or 1d"
                        isSingleLine = true
                    }
                    val dialog = requireContext().getAlertDialogBuilder().setTitle("Custom timeout")
                        .setView(field).setNegativeButton("Cancel", null).setPositiveButton("Continue", null).create()
                    dialog.setOnShowListener {
                        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            val duration = com.github.andreyasadchy.xtra.ui.chat.TimeoutDuration.helixSeconds(field.text.toString())
                            if (duration == null) field.error = "Use 1 second to 14 days, for example 10m."
                            else { dialog.dismiss(); personAction("Timeout", username, reason, duration) }
                        }
                    }
                    showAlert(dialog)
                }
            }.setNegativeButton("Cancel", null).create()
        showAlert(choice)
    }
    private fun personAction(kind: String, enteredUsername: String, text: String, duration: Int?) {
        val username = enteredUsername.trim().removePrefix("@")
        if (!username.matches(Regex("[A-Za-z0-9_]{1,25}"))) { notice.setText(R.string.hex_mod_enter_user); return }
        if (kind == "Warn" && text.isBlank()) { notice.setText(R.string.hex_mod_warn_reason); return }
        val length = duration?.let(::durationLabel)
        confirm("$kind $username?", "Channel: $channelLogin\n${length?.let { "Duration: $it\n" }.orEmpty()}${text.takeIf { it.isNotBlank() }?.let { "Reason: $it" }.orEmpty()}", kind) {
            task(true) { session ->
                val selectedLogin = requireArguments().getString("target_login")
                val selectedId = requireArguments().getString("target_id")
                val id = selectedId?.takeIf { username.equals(selectedLogin, true) && it.matches(Regex("[0-9]+")) }
                    ?: session.user(username).text("id")
                when (kind) {
                    "Ban" -> session.ban(channelId, id, text.trim())
                    "Timeout" -> session.ban(channelId, id, text.trim(), requireNotNull(duration))
                    "Unban" -> session.unban(channelId, id)
                    "Warn" -> session.warn(channelId, id, text.trim())
                }
            }
        }
    }
    private fun durationLabel(seconds: Int): String {
        val (amount, unit) = when {
            seconds % 86400 == 0 -> seconds / 86400 to "day"
            seconds % 3600 == 0 -> seconds / 3600 to "hour"
            seconds % 60 == 0 -> seconds / 60 to "minute"
            else -> seconds to "second"
        }
        return "$amount $unit${if (amount == 1) "" else "s"}"
    }
    private fun chat() {
        clear("Chat controls • $channelLogin")
        val current = label("Load current modes to see what Twitch has enabled.")
        button("Refresh current modes") { refreshChat(current) }
        val slow = input("Slow mode seconds • 3–120", "10", number = true)
        val age = input("Follower age in minutes • 0 lets new followers chat", "0", number = true)
        listOf("slow_mode" to "Slow mode", "follower_mode" to "Followers only", "subscriber_mode" to "Subscribers only", "emote_mode" to "Emotes only", "unique_chat_mode" to "Unique chat").forEach { (key, title) ->
            button("$title • change") {
                requireContext().getAlertDialogBuilder().setTitle(title).setItems(arrayOf("Enable", "Disable")) { _, which ->
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
        val queue = android.widget.LinearLayout(requireContext()).apply { orientation = android.widget.LinearLayout.VERTICAL }; body.addView(queue)
        button("Listen for new held messages") {
            val session = api ?: return@button
            queueJob?.cancel(); held.clear(); renderHeld(queue)
            queueJob = lifecycleScope.launch {
                val condition = buildJsonObject { put("broadcaster_user_id", channelId); put("moderator_user_id", session.userId) }
                try { session.requireModerator(channelId); TwitchEvents.run(session, mapOf("automod.message.hold" to condition, "automod.message.update" to condition), { queueStatus.text = it }) { type, event ->
                    if (event.text("broadcaster_user_id") != channelId) return@run
                    val id = event.text("message_id")
                    if (type == "automod.message.hold") { held[id] = event; while (held.size > 100) held.remove(held.keys.first()) } else held.remove(id)
                    renderHeld(queue)
                } } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { queueStatus.text = getString(R.string.hex_tools_queue_failed) }
            }
        }
        button("Full AutoMod queue") { TwitchPollDialog.newInstance(channelId, channelLogin, true).show(childFragmentManager, "fullAutomod") }
        button("View AutoMod settings") { task { session ->
            val data = session.automodSettings(channelId).rows().firstOrNull()?.jsonObject
            label(data?.entries?.filterNot { it.key.endsWith("_id") }?.joinToString("\n") { "${it.key.replace('_', ' ')}: ${it.value}" } ?: "No settings returned")
        } }
        button("Set AutoMod overall level") {
            requireContext().getAlertDialogBuilder().setTitle("AutoMod level").setItems(arrayOf("0 • off", "1 • low", "2 • medium", "3 • high", "4 • maximum")) { _, level ->
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
            val card = android.widget.LinearLayout(requireContext()).apply { orientation = android.widget.LinearLayout.VERTICAL; tag = id }
            queue.addView(card)
            val text = event.heldMessageText()
            card.addView(android.widget.TextView(requireContext()).apply { this.text = getString(R.string.hex_tools_held_message, event.text("user_name"), text); setPadding(dp(4), dp(12), dp(4), dp(8)) })
            listOf(true, false).forEach { allow -> card.addView(android.widget.Button(requireContext()).apply {
                this.text = if (allow) "Allow message" else "Deny message"; isAllCaps = false
                setOnClickListener { if (!busy) confirm(if (allow) "Allow this message?" else "Deny this message?", text) { task(true) { it.automodMessage(channelId, id, allow); held.remove(id); renderHeld(queue) } } }
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
            if ("terms:$after" in loadedPages) {
                return@task
            }
            val result = session.terms(channelId, after)
            loadedPages += "terms:$after"
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
            if ("unbans:$after" in loadedPages) {
                return@task
            }
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
