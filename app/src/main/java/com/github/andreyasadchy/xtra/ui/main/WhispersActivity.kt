package com.github.andreyasadchy.xtra.ui.main

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.repository.WhisperPeer
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.util.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class WhispersActivity : AppCompatActivity(), IntegrityDialog.Listener {
    private val model: WhispersHistoryViewModel by viewModels()
    private lateinit var heading: TextView
    private lateinit var status: TextView
    private lateinit var connection: TextView
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var refresh: Button
    private lateinit var newConversation: Button
    private lateinit var more: Button
    private lateinit var verify: Button
    private lateinit var composer: LinearLayout
    private lateinit var draft: EditText
    private lateinit var send: Button
    private val rows = Rows()
    private var showingPeer: String? = null
    private var settingDraft = false
    private var lastState = WhispersScreen()
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 15f) = TextView(this).apply { text = value; textSize = size; setPadding(dp(12), dp(8), dp(12), dp(8)) }
    private fun button(resource: Int, action: () -> Unit) = Button(this).apply {
        text = getString(resource); isAllCaps = false; minHeight = dp(48); setOnClickListener { action() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(); super.onCreate(savedInstanceState)
        WhisperCenter.initialize(application)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(button(R.string.hex_tools_back) { back() })
        heading = text(getString(R.string.hex_whispers_title), 23f); header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button(R.string.hex_whisper_help_button) {
            getAlertDialogBuilder().setTitle(R.string.hex_whispers_title).setMessage(R.string.hex_whisper_history_help)
                .setPositiveButton(android.R.string.ok, null).setNeutralButton(R.string.hex_whisper_clear_cache) { _, _ ->
                    getAlertDialogBuilder().setTitle(R.string.hex_whisper_clear_cache).setMessage(R.string.hex_whisper_clear_cache_confirm)
                        .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok) { _, _ -> model.clearLocal() }.show()
                }.show()
        })
        root.addView(header)
        root.addView(text(getString(R.string.hex_tools_account, tokenPrefs().getString(C.USERNAME, "Twitch")), 12f))
        if (!prefs().getBoolean(WhisperCenter.ENABLED, false)) {
            root.addView(text(getString(R.string.hex_whispers_off), 22f))
            root.addView(text(getString(R.string.hex_whispers_enable_hint)))
            setContentView(root); return
        }
        status = text("", 14f).apply { id = R.id.hex_whisper_history_status; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }; root.addView(status)
        connection = text(WhisperCenter.status.value, 12f); root.addView(connection)
        val actions = LinearLayout(this)
        newConversation = button(R.string.hex_whisper_new) {
            val input = EditText(this).apply { hint = getString(R.string.hex_whisper_username); isSingleLine = true }
            getAlertDialogBuilder().setTitle(R.string.hex_whisper_new).setView(input).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ -> model.find(input.text.toString()) }.show()
        }
        refresh = button(R.string.hex_whisper_refresh) { model.refresh() }
        actions.addView(newConversation, LinearLayout.LayoutParams(0, -2, 1f)); actions.addView(refresh, LinearLayout.LayoutParams(0, -2, 1f)); root.addView(actions)
        verify = button(R.string.hex_whisper_verify) { if (supportFragmentManager.findFragmentByTag("whisper-verification") == null) IntegrityDialog.forWhispers(model.state.value.accountId).show(supportFragmentManager, "whisper-verification") }
        root.addView(verify)
        more = button(R.string.hex_whisper_more_threads) { if (model.state.value.selected == null) model.loadThreads() else model.loadMessages() }; root.addView(more)
        empty = text("", 16f).apply { gravity = Gravity.CENTER; setPadding(dp(24), dp(20), dp(24), dp(20)) }; root.addView(empty)
        list = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@WhispersActivity); adapter = rows; clipToPadding = false; setPadding(dp(8), 0, dp(8), dp(8)) }
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val state = model.state.value
                if (state.loading || state.sending) return
                val manager = recyclerView.layoutManager as LinearLayoutManager
                if (state.selected == null && dy > 0 && state.moreThreads && manager.findLastVisibleItemPosition() >= rows.itemCount - 2) model.loadThreads()
                if (state.selected != null && dy < 0 && state.moreMessages && manager.findFirstVisibleItemPosition() == 0) model.loadMessages()
            }
        })
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        composer = LinearLayout(this).apply { gravity = Gravity.BOTTOM; setPadding(dp(8), dp(4), dp(8), dp(8)) }
        draft = EditText(this).apply {
            hint = getString(R.string.hex_whisper_message); minLines = 1; maxLines = 5; textSize = 16f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (!settingDraft) model.draft(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        composer.addView(draft, LinearLayout.LayoutParams(0, -2, 1f))
        send = button(R.string.hex_whisper_send) {
            val peer = model.state.value.selected ?: return@button
            val message = draft.text.toString()
            getAlertDialogBuilder().setTitle(getString(R.string.hex_whisper_send_confirm, peer.name.ifBlank { peer.login }))
                .setMessage(message).setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.hex_whisper_send) { _, _ -> model.send(message) }.show()
        }
        composer.addView(send); root.addView(composer)
        setContentView(root)
        model.attach(this)
        onBackPressedDispatcher.addCallback(this) { back() }
        if (model.state.value.selected == null && savedInstanceState?.getString("account_id") == model.state.value.accountId && savedInstanceState.getString("peer_id")?.isNotBlank() == true) {
            model.select(WhisperPeer(savedInstanceState.getString("peer_id").orEmpty(), savedInstanceState.getString("peer_login").orEmpty(), savedInstanceState.getString("peer_name").orEmpty()))
            model.draft(savedInstanceState.getString("draft").orEmpty())
        } else if (savedInstanceState == null) intent.getStringExtra("target_login")?.takeIf { it.isNotBlank() }?.let { model.find(it) }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { model.state.collect { render(it) } }
            launch { WhisperCenter.status.collect { connection.text = it } }
        } }
    }
    override fun onStart() {
        super.onStart()
        if (::status.isInitialized) model.refresh()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("account_id", model.state.value.accountId)
        model.state.value.selected?.let { outState.putString("peer_id", it.id); outState.putString("peer_login", it.login); outState.putString("peer_name", it.name); outState.putString("draft", model.draft()) }
        super.onSaveInstanceState(outState)
    }
    private fun back() { if (::status.isInitialized && model.state.value.selected != null) model.inbox() else finish() }
    override fun onIntegrityTokenLoaded(callback: String?) { if (callback == "whispers") model.retryVerifiedHistory() }
    private fun render(state: WhispersScreen) {
        val changedPeer = showingPeer != state.selected?.id
        val atBottom = !list.canScrollVertically(1)
        showingPeer = state.selected?.id
        heading.text = state.selected?.name?.ifBlank { state.selected.login } ?: getString(R.string.hex_whispers_title)
        status.text = state.status
        verify.isVisible = state.verificationRequired; verify.isEnabled = !state.loading
        refresh.isEnabled = !state.loading && !state.sending
        newConversation.isVisible = state.selected == null; newConversation.isEnabled = !state.loading && !state.sending
        composer.isVisible = state.selected != null; send.isEnabled = !state.sending
        more.isVisible = if (state.selected == null) state.moreThreads else state.moreMessages
        more.isEnabled = !state.loading && !state.sending
        more.setText(if (state.selected == null) R.string.hex_whisper_more_threads else R.string.hex_whisper_older)
        val nextRows = if (state.selected == null) state.contacts.map { contact ->
            Row("c:${contact.peer.id}", contact.peer.name.ifBlank { contact.peer.login }.ifBlank { contact.peer.id }, contact.latest?.text.orEmpty(), contact.peer)
        } else state.messages.map { message ->
            Row("m:${message.id}", getString(R.string.hex_whisper_message_heading, message.from, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(message.time))), message.text)
        }
        empty.isVisible = nextRows.isEmpty() && !state.loading
        empty.setText(if (state.selected == null) R.string.hex_whisper_empty_threads else R.string.hex_whisper_empty_messages)
        val previousNewest = lastState.messages.lastOrNull()?.id
        rows.submitList(nextRows) {
            if (state.selected != null && (changedPeer || (atBottom && previousNewest != state.messages.lastOrNull()?.id))) list.scrollToPosition((nextRows.size - 1).coerceAtLeast(0))
        }
        val desired = model.draft()
        if (changedPeer || (!state.sending && draft.text.toString() != desired)) {
            settingDraft = true; draft.setText(desired); draft.setSelection(draft.text.length); settingDraft = false
        }
        lastState = state
    }
    private data class Row(val id: String, val primary: String, val secondary: String, val peer: WhisperPeer? = null)
    private inner class Holder(val root: LinearLayout, val primary: TextView, val secondary: TextView) : RecyclerView.ViewHolder(root)
    private inner class Rows : ListAdapter<Row, Holder>(object : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(old: Row, new: Row) = old.id == new.id
        override fun areContentsTheSame(old: Row, new: Row) = old == new
    }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val root = LinearLayout(this@WhispersActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)); minimumHeight = dp(64) }
            val primary = text("", 16f); val secondary = text("", 15f)
            root.addView(primary); root.addView(secondary); return Holder(root, primary, secondary)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = getItem(position); holder.primary.text = row.primary; holder.secondary.text = row.secondary
            holder.secondary.setTextIsSelectable(row.peer == null)
            holder.root.isClickable = row.peer != null; holder.root.isFocusable = row.peer != null
            holder.root.setOnClickListener(if (row.peer != null) View.OnClickListener { model.select(row.peer) } else null)
        }
    }
}
