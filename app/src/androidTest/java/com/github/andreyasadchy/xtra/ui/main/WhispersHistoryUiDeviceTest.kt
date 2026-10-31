package com.github.andreyasadchy.xtra.ui.main

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.repository.WhisperPeer
import com.github.andreyasadchy.xtra.util.*
import org.junit.Assert.*
import org.junit.Test

class WhispersHistoryUiDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun views(root: View): List<View> = buildList {
        add(root); if (root is ViewGroup) for (index in 0 until root.childCount) addAll(views(root.getChildAt(index)))
    }
    private fun fixture(test: (WhispersActivity) -> Unit) {
        val context = instrumentation.targetContext
        check(context.packageName == "com.github.andreyasadchy.xtra.qa")
        check(context.tokenPrefs().getString(C.USER_ID, null).isNullOrBlank())
        val before = context.prefs().getBoolean(WhisperCenter.ENABLED, false)
        context.prefs().edit().putBoolean(WhisperCenter.ENABLED, true).commit()
        val activity = instrumentation.startActivitySync(Intent(context, WhispersActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WhispersActivity
        try { test(activity) } finally {
            instrumentation.runOnMainSync { activity.finish() }
            context.prefs().edit().putBoolean(WhisperCenter.ENABLED, before).commit()
        }
    }
    private fun awaitText(activity: WhispersActivity, text: String) {
        repeat(100) {
            var found = false
            instrumentation.runOnMainSync { found = views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == text } }
            if (found) return
            SystemClock.sleep(30)
        }
        fail("Synthetic history row did not render")
    }
    @Test fun existingConversationAndOlderMessageRenderWithAStableComposer() = fixture { activity ->
        val peer = WhisperPeer("22", "fixture_peer", "Fixture contact")
        val old = WhisperEntry("old-message", "22", peer.name, "11", "Fixture owner", "An older test message", 1_577_836_800_000)
        instrumentation.runOnMainSync {
            val model = ViewModelProvider(activity)[WhispersHistoryViewModel::class.java]
            model.state.value = WhispersScreen(contacts = listOf(WhisperContact(peer, "thread", old)), historyLoaded = true, status = "Synthetic history loaded")
        }
        awaitText(activity, "Fixture contact")
        instrumentation.runOnMainSync {
            val model = ViewModelProvider(activity)[WhispersHistoryViewModel::class.java]
            model.state.value = model.state.value.copy(selected = peer, messages = listOf(old), moreMessages = true)
        }
        awaitText(activity, "An older test message")
        instrumentation.runOnMainSync {
            val model = ViewModelProvider(activity)[WhispersHistoryViewModel::class.java]
            val draft = views(activity.window.decorView).filterIsInstance<EditText>().single()
            draft.setText("Synthetic draft kept during history loading")
            model.state.value = model.state.value.copy(loading = true, verificationRequired = true)
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val draft = views(activity.window.decorView).filterIsInstance<EditText>().single()
            assertEquals("Synthetic draft kept during history loading", draft.text.toString())
            assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Load older messages" && it.visibility == View.VISIBLE })
            assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Verify Twitch login" && it.visibility == View.VISIBLE })
        }
    }
    @Test fun missingAuthenticationIsExplicitAndNeverReportedAsAnEmptyTwitchInbox() = fixture { activity ->
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val model = ViewModelProvider(activity)[WhispersHistoryViewModel::class.java]
            assertFalse(model.state.value.historyLoaded)
            val status = activity.findViewById<TextView>(R.id.hex_whisper_history_status).text.toString()
            assertTrue(status.contains("Twitch login"))
            assertFalse(status.contains("Twitch returned no conversations"))
        }
    }
}
