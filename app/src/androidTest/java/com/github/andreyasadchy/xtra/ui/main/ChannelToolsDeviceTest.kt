package com.github.andreyasadchy.xtra.ui.main

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.github.andreyasadchy.xtra.util.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic QA only. No live messages, clips, follows or moderation operations. */
class ChannelToolsDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun context() = instrumentation.targetContext.also {
        check(it.packageName == "com.github.andreyasadchy.xtra.qa") { "Use only the isolated QA package" }
        check(it.tokenPrefs().getString(C.USER_ID, null).isNullOrBlank()) { "Use an account-free QA installation" }
    }
    private fun text(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(text(view.getChildAt(index)))
    }
    @Test fun disabledWhispersExplainHowToEnableWithoutConnecting() {
        val context = context(); val before = context.prefs().getBoolean("hex_whispers", false)
        context.prefs().edit().putBoolean("hex_whispers", false).commit()
        val activity = instrumentation.startActivitySync(Intent(context, WhispersActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try { instrumentation.runOnMainSync {
            assertTrue(text(activity.window.decorView).any { it == "Whispers are off" })
        } } finally { instrumentation.runOnMainSync { activity.finish() }; context.prefs().edit().putBoolean("hex_whispers", before).commit() }
    }
    @Test fun moderatorEntryOffersChannelSelectionWithoutFollowing() {
        val context = context(); val before = context.prefs().getBoolean("hex_moderator_tools", true)
        context.prefs().edit().putBoolean("hex_moderator_tools", true).commit()
        val activity = instrumentation.startActivitySync(Intent(context, ModeratorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try { instrumentation.runOnMainSync {
            val text = text(activity.window.decorView)
            assertFalse(text.contains("Channel username")); assertTrue(text.contains("Load channels I moderate"))
        } } finally { instrumentation.runOnMainSync { activity.finish() }; context.prefs().edit().putBoolean("hex_moderator_tools", before).commit() }
    }
    @Test fun unverifiedChannelContextHasNoActionsOrEditableChannelSelector() {
        val context = context()
        val activity = instrumentation.startActivitySync(Intent(context, ModeratorActivity::class.java).putExtra("channel_id", "123").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try { instrumentation.runOnMainSync {
            val values = text(activity.window.decorView)
            assertFalse(values.contains("Channel username"))
            assertFalse(values.contains("People • ban, timeout, unban, warn"))
            assertFalse(values.contains("Switch channel"))
        } } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun clipEntryExplainsChannelRestrictionsAndNeedsExplicitRequest() {
        val context = context()
        val activity = instrumentation.startActivitySync(Intent(context, CreateClipActivity::class.java).putExtra("channel_id", "123").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try { instrumentation.runOnMainSync {
            val text = text(activity.window.decorView)
            assertTrue(text.contains("Capture clip now")); assertTrue(text.any { it.contains("You do not need to follow or moderate") })
            assertTrue(text.any { it.contains("Connect your active account") })
        } } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun whisperHistoryIsEncryptedBoundedAndSeparateForEachAccount() {
        val context = context()
        val a = "synthetic-account-a"; val b = "synthetic-account-b"
        val phrase = "Synthetic private whisper fixture"
        val first = WhisperEntry("1", "1", "One", "2", "Two", phrase, 1)
        try {
            WhisperHistory.write(context, a, (1..600).map { first.copy(id = it.toString()) })
            WhisperHistory.write(context, b, listOf(first.copy(id = "other")))
            assertEquals(500, WhisperHistory.read(context, a).size)
            assertEquals("101", WhisperHistory.read(context, a).first().id)
            assertFalse(context.getSharedPreferences("hex_whisper_history", 0).all.values.any { it.toString().contains(phrase) })
            WhisperHistory.clear(context, a)
            assertTrue(WhisperHistory.read(context, a).isEmpty())
            assertEquals("other", WhisperHistory.read(context, b).single().id)
        } finally { WhisperHistory.clear(context, a); WhisperHistory.clear(context, b) }
    }
}
