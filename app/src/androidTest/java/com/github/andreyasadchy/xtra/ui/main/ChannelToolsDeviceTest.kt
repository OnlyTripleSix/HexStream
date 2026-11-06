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
    @Test fun selectedMessageProvidesDirectBanTimeoutAndDeleteButtons() {
        val context = context()
        instrumentation.runOnMainSync {
            val themed = androidx.appcompat.view.ContextThemeWrapper(context, com.github.andreyasadchy.xtra.R.style.DarkTheme)
            val view = android.view.LayoutInflater.from(themed).inflate(com.github.andreyasadchy.xtra.R.layout.dialog_chat_message_click, null)
            assertEquals("Ban", view.findViewById<TextView>(com.github.andreyasadchy.xtra.R.id.banUser).text.toString())
            assertEquals("Timeout…", view.findViewById<TextView>(com.github.andreyasadchy.xtra.R.id.timeoutUser).text.toString())
            assertEquals("Delete", view.findViewById<TextView>(com.github.andreyasadchy.xtra.R.id.deleteUserMessage).text.toString())
            assertEquals(View.GONE, view.findViewById<View>(com.github.andreyasadchy.xtra.R.id.moderationGroup).visibility)
        }
    }
    @Test fun unverifiedChannelSheetHasNoActionsOrEditableChannelSelector() {
        val context = context()
        val activity = instrumentation.startActivitySync(Intent(context, com.github.andreyasadchy.xtra.ui.settings.SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
        val sheet = ChannelModeratorDialog.newInstance("123", "fixture", "456", "fixture_user", quickAction = "ban")
        try {
            instrumentation.runOnMainSync { sheet.showNow(activity.supportFragmentManager, "moderatorFixture") }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val values = text(sheet.requireView())
                assertFalse(values.contains("Channel username"))
                assertFalse(values.contains("Ban"))
                assertTrue(values.any { it.contains("Connect your active account") || it.contains("Reconnect") })
                assertEquals("123", sheet.requireArguments().getString("channel_id"))
            }
        } finally { instrumentation.runOnMainSync { sheet.dismissNow(); activity.finish() } }
    }
    @Test fun quickBanConfirmationAppearsAboveSheetAndClosesWithIt() {
        val context = context()
        val tokens = context.tokenPrefs()
        val keys = listOf(C.USER_ID, C.USERNAME, C.TOKEN)
        val before = keys.associateWith { tokens.getString(it, null) }
        val whispers = context.prefs().getBoolean("hex_whispers", false)
        val activity = instrumentation.startActivitySync(Intent(context, com.github.andreyasadchy.xtra.ui.settings.SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
        val sheet = ChannelModeratorDialog.newInstance("111111", "fixture_channel", "222222", "fixture_user", quickAction = "ban")
        try {
            context.prefs().edit().putBoolean("hex_whispers", false).commit()
            tokens.edit().putString(C.USER_ID, "111111").putString(C.USERNAME, "Fixture").putString(C.TOKEN, "fixture").commit()
            instrumentation.runOnMainSync {
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                sheet.showNow(activity.supportFragmentManager, "quickBanFixture")
            }
            val deadline = android.os.SystemClock.uptimeMillis() + 3000
            fun confirmationVisible() = instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText("Ban fixture_user?")?.any { it.text?.toString() == "Ban fixture_user?" } == true
            while (!confirmationVisible() && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(40)
            assertTrue("Confirmation must be the active window, above the channel sheet", confirmationVisible())
            instrumentation.runOnMainSync { sheet.dismissNow() }
            instrumentation.waitForIdleSync()
            assertFalse("Closing the sheet must remove its pending confirmation", confirmationVisible())
        } finally {
            instrumentation.runOnMainSync { if (sheet.isAdded) sheet.dismissNow(); activity.finish() }
            tokens.edit().apply { before.forEach { (key, value) -> putString(key, value) } }.commit()
            context.prefs().edit().putBoolean("hex_whispers", whispers).commit()
        }
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
