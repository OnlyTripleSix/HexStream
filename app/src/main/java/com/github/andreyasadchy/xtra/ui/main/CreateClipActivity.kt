package com.github.andreyasadchy.xtra.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import com.github.andreyasadchy.xtra.R
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class CreateClipViewModel : ViewModel() {
    data class State(val attempted: Boolean = false, val pending: Boolean = false, val editUrl: String? = null, val error: String? = null)
    val state = MutableStateFlow(State())
    fun create(api: ChannelActionsApi, channelId: String) {
        if (state.value.attempted) return
        state.value = State(attempted = true, pending = true)
        viewModelScope.launch {
            try {
                val clip = api.createClip(channelId)
                val url = ChannelActionsApi.clipEditUrl(clip.text("edit_url")) ?: throw ActionFailure("Twitch returned no usable editor URL. Check your Twitch clips before retrying.")
                state.value = State(attempted = true, editUrl = url)
            } catch (e: CancellationException) { throw e }
            catch (e: ActionFailure) { state.value = State(attempted = true, error = e.message) }
            catch (_: Exception) { state.value = State(attempted = true, error = "The request was interrupted. A clip may already exist. Check Twitch before starting another clip.") }
        }
    }
}

class CreateClipActivity : ChannelActionActivity() {
    private val model: CreateClipViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clear("Create a clip")
        val channel = intent.getStringExtra("channel_id").orEmpty()
        label("Clip this live moment, then use Twitch's editor to trim, title and publish/share it. You do not need to follow or moderate the channel. Its clip restrictions still apply.")
        val result = label("")
        val restoredAttempt = savedInstanceState?.getBoolean("clip_attempted") == true && !model.state.value.attempted
        val create = button("Capture clip now") {
            api?.let { session -> confirm("Create a clip?", "This submits one clip request to Twitch. The editor opens separately once Twitch accepts it.") { model.create(session, channel) } }
                ?: run { notice.text = getString(R.string.hex_tools_reconnect) }
        }
        val edit = button("Open Twitch clip editor") { model.state.value.editUrl?.let { openTwitch(it) } }
        val copy = button("Copy clip editor link") { model.state.value.editUrl?.let { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Clip editor", it)) } }
        lifecycleScope.launch { model.state.collect { state ->
            create.isEnabled = channel.isNotBlank() && !state.attempted && !restoredAttempt
            edit.isEnabled = state.editUrl != null; copy.isEnabled = state.editUrl != null
            result.text = when {
                restoredAttempt -> "A request was started before this screen was restored. Check Twitch for the clip before requesting another."
                state.pending -> "Creating clip…"
                state.error != null -> state.error
                state.editUrl != null -> "Twitch returned the editor link. It can take a few seconds to become ready. The browser uses its own Twitch login."
                else -> "Ready. Clip timing follows Twitch's capture window, so open the editor to choose the exact moment."
            }
        } }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("clip_attempted", model.state.value.attempted); super.onSaveInstanceState(outState) }
}
