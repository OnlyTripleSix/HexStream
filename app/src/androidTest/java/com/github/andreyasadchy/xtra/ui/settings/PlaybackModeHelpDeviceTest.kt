package com.github.andreyasadchy.xtra.ui.settings

import android.content.DialogInterface
import android.content.Intent
import android.os.SystemClock
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.test.platform.app.InstrumentationRegistry
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.CustomProxy
import com.github.andreyasadchy.xtra.ui.player.PlaybackModeHelp
import com.github.andreyasadchy.xtra.util.PlaybackProxyPresets
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.Assert.*
import org.junit.Test

class PlaybackModeHelpDeviceTest {
    private fun withSettings(block: (SettingsActivity) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals("Use the isolated QA package", "com.github.andreyasadchy.xtra.qa", context.packageName)
        val activity = instrumentation.startActivitySync(Intent(context, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SettingsActivity
        try {
            instrumentation.runOnMainSync { activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            instrumentation.waitForIdleSync()
            block(activity)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun helpExplainsStreamlinkAndOpensTheSharedProxyListWithoutChangingMode() = withSettings { activity ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val mode = activity.prefs().getString("hex_playback_mode", null)
        instrumentation.runOnMainSync {
            val before = activity.prefs().all.toMap()
            val dialog = PlaybackModeHelp.show(activity)
            val text = dialog.findViewById<TextView>(android.R.id.message)!!.text.toString()
            assertTrue(text.contains("Automatic (Streamlink)"))
            assertTrue(text.contains("Luminous/PerfProd"))
            assertTrue(text.contains("Playback URL presets and custom endpoints"))
            assertEquals(before, activity.prefs().all)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        SystemClock.sleep(500)
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val host = activity.supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
            assertEquals(R.id.customProxySettingsFragment, host.navController.currentDestination?.id)
            assertEquals(mode, activity.prefs().getString("hex_playback_mode", null))
        }
    }

    @Test fun selectorOffersHelpAndCancellingDoesNotChangeTheMode() = withSettings { activity ->
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val before = activity.prefs().all.toMap()
            var selected = -1
            val dialog = PlaybackModeHelp.showSelector(activity, 0) { selected = it }
            assertEquals(activity.getString(R.string.hex_modes_help), dialog.getButton(DialogInterface.BUTTON_NEUTRAL).text)
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
            assertEquals(-1, selected)
            assertEquals(before, activity.prefs().all)
        }
    }

    @Test fun rebindingProxyRowsPreservesChoicesAndHttpEntriesDoNotShowFalseOffline() = withSettings { activity ->
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val host = activity.supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
            host.childFragmentManager.executePendingTransactions()
            val fragment = host.childFragmentManager.fragments.first()
            val adapter = CustomProxySettingsAdapter(fragment)
            val first = CustomProxy(PlaybackProxyPresets.httpUrls[0], false, 0, false).apply { id = 1 }
            val second = CustomProxy(PlaybackProxyPresets.httpUrls[1], false, 1, true).apply { id = 2 }
            val changed = mutableListOf<Int>()
            adapter.checkListener = { changed.add(it.id) }
            adapter.statusMap = mapOf(first.url!! to false, second.url!! to false)
            val row = adapter.onCreateViewHolder(FrameLayout(activity), 0)
            row.bind(first)
            row.bind(second)
            assertFalse(first.enabled)
            assertTrue(second.enabled)
            assertTrue(changed.isEmpty())
            assertEquals(activity.getString(R.string.hex_ttv_proxy_chromium), row.itemView.findViewById<TextView>(R.id.text).text)
            assertEquals(activity.getString(R.string.hex_http_proxy_status), row.itemView.findViewById<TextView>(R.id.status).text)
            row.itemView.findViewById<CheckBox>(R.id.checkBox).performClick()
            assertEquals(listOf(2), changed)
            assertFalse(first.enabled)
            assertFalse(second.enabled)
        }
    }
}
