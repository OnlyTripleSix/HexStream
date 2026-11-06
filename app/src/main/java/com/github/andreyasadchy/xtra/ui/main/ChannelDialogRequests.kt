package com.github.andreyasadchy.xtra.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.repository.ActionFailure
import com.github.andreyasadchy.xtra.repository.ChannelActionsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class ChannelDialogState(val busy: Boolean = false, val message: String = "")

class ChannelDialogRequests : ViewModel() {
    val state = MutableStateFlow(ChannelDialogState())

    fun execute(session: ChannelActionsApi, mutation: Boolean, block: suspend (ChannelActionsApi) -> Unit) {
        if (state.value.busy) return
        state.value = ChannelDialogState(true, if (mutation) "Submitting to Twitch…" else "Checking Twitch…")
        viewModelScope.launch {
            val message = try {
                session.checkCurrent()
                block(session)
                session.checkCurrent()
                if (mutation) "Twitch accepted the action." else ""
            } catch (error: CancellationException) { throw error }
            catch (error: IllegalArgumentException) { error.message ?: "Check the entered values." }
            catch (error: ActionFailure) { error.message.orEmpty() }
            catch (_: Exception) { "Could not confirm the result. Check the channel before repeating the action." }
            state.value = ChannelDialogState(message = message)
        }
    }
}
