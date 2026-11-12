package com.github.andreyasadchy.xtra.ui.settings

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.TextView
import android.view.WindowManager
import androidx.core.view.children
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.forEach
import androidx.test.platform.app.InstrumentationRegistry
import com.github.andreyasadchy.xtra.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsHelpDeviceTest {
    private fun onMain(block: () -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertEquals("Use the isolated QA package", "com.github.andreyasadchy.xtra.qa", instrumentation.targetContext.packageName)
        instrumentation.runOnMainSync(block)
    }

    @Test fun changingValuesKeepsTheExplanationAndDoesNotWritePreferences() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = PreferenceManager(context)
        val screen = manager.createPreferenceScreen(context)
        val choice = ListPreference(context).apply {
            isPersistent = false
            entries = arrayOf("First", "Second")
            entryValues = arrayOf("one", "two")
            value = "one"
            summary = "Choose a route. Example: use Second for another route."
        }
        val number = EditTextPreference(context).apply {
            isPersistent = false
            key = "proxy_timeout"
            text = "3000"
            summary = "Milliseconds. Example: 3000 means three seconds."
        }
        screen.addPreference(choice)
        screen.addPreference(number)
        val before = manager.sharedPreferences!!.all.toMap()
        SettingsHelp.attach(screen)
        choice.value = "two"
        number.text = "5000"
        assertTrue(choice.summary.toString().startsWith("Current: Second\n"))
        assertTrue(choice.summary.toString().contains("Example:"))
        assertTrue(number.summary.toString().startsWith("Current: 5000\n"))
        assertTrue(number.summary.toString().contains("Milliseconds."))
        assertEquals(before, manager.sharedPreferences!!.all)
    }

    @Test fun credentialsHeadersAndAccountDetailsNeverEnterSummaries() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = PreferenceManager(context)
        val screen = manager.createPreferenceScreen(context)
        for (key in listOf("token", "gql_token2", "gql_token_web", "gql_headers", "user_id", "username")) {
            screen.addPreference(EditTextPreference(context).apply {
                isPersistent = false
                this.key = key
                text = "fixture-private-value-$key"
                summary = "Account help. Example: use normal sign-in."
            })
        }
        SettingsHelp.attach(screen)
        screen.forEach {
            assertTrue(it.summary.toString().contains("Example:"))
            assertFalse(it.summary.toString().contains("fixture-private-value"))
        }
    }

    @Test fun everySettingsRowInflatesWithAnExample() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resources = listOf(
            R.xml.root_preferences, R.xml.player_preferences, R.xml.buffer_preferences,
            R.xml.playback_preferences, R.xml.player_button_preferences, R.xml.player_menu_preferences,
            R.xml.chat_preferences, R.xml.ui_preferences, R.xml.theme_preferences,
            R.xml.download_preferences, R.xml.update_preferences, R.xml.api_token_preferences, R.xml.debug_preferences,
        )
        var count = 0
        fun check(group: PreferenceGroup) {
            group.forEach {
                if (it is PreferenceGroup) check(it)
                else if (it !is PreferenceCategory && it.key != null) {
                    assertTrue("Missing help: ${it.key}", it.summary.toString().contains("Example:"))
                    assertFalse("Unexpanded selection: ${it.key}", it.summary.toString().contains("%s"))
                    count++
                }
            }
        }
        for (resource in resources) {
            val manager = PreferenceManager(context)
            manager.sharedPreferencesName = "settings_help_fixture"
            val screen = manager.inflateFromResource(context, resource, null)
            val before = manager.sharedPreferences!!.all.toMap()
            SettingsHelp.attach(screen)
            check(screen)
            assertEquals(before, manager.sharedPreferences!!.all)
        }
        assertEquals(232, count)
    }

    @Test fun settingsScreensRenderWithFullSummaries() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals("Use the isolated QA package", "com.github.andreyasadchy.xtra.qa", context.packageName)
        val activity = instrumentation.startActivitySync(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SettingsActivity
        try {
            instrumentation.runOnMainSync {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            instrumentation.waitForIdleSync()
            val destinations = listOf(
                R.id.settingsFragment, R.id.playerSettingsFragment, R.id.bufferSettingsFragment,
                R.id.playbackSettingsFragment, R.id.playerButtonSettingsFragment, R.id.playerMenuSettingsFragment,
                R.id.chatSettingsFragment, R.id.uiSettingsFragment, R.id.themeSettingsFragment,
                R.id.downloadSettingsFragment, R.id.updateSettingsFragment, R.id.apiTokenSettingsFragment, R.id.debugSettingsFragment,
            )
            for (destination in destinations) {
                instrumentation.runOnMainSync {
                    val host = activity.supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
                    if (host.navController.currentDestination?.id != destination) host.navController.navigate(destination)
                    host.childFragmentManager.executePendingTransactions()
                }
                // Idle does not wait for the window/fragment transition to finish.
                SystemClock.sleep(500)
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    val host = activity.supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
                    assertEquals(destination, host.navController.currentDestination?.id)
                    val fragment = host.childFragmentManager.primaryNavigationFragment as MaterialPreferenceFragment
                    val summaries = fragment.listView.children.mapNotNull { it.findViewById<TextView>(android.R.id.summary) }
                        .filter { it.text.isNotEmpty() }.toList()
                    assertTrue("No rendered help at $destination", summaries.isNotEmpty())
                    summaries.forEach { assertEquals(Int.MAX_VALUE, it.maxLines) }
                }
                if (destination in listOf(R.id.settingsFragment, R.id.bufferSettingsFragment)) {
                    val bitmap = instrumentation.uiAutomation.takeScreenshot()
                    File(context.externalCacheDir, "settings-help-$destination.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
