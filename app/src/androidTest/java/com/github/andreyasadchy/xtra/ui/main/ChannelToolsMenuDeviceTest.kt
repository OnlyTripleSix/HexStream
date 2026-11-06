package com.github.andreyasadchy.xtra.ui.main

import android.content.Context
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.Toolbar
import androidx.test.platform.app.InstrumentationRegistry
import com.github.andreyasadchy.xtra.R
import org.junit.Assert.*
import org.junit.Test

class ChannelToolsMenuDeviceTest {
    @Test fun settingsChangesRefreshExistingNestedToolbarWithoutRecreatingViews() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "com.github.andreyasadchy.xtra.qa")
        val preferences = context.getSharedPreferences("channel_menu_visibility_fixture", Context.MODE_PRIVATE)
        try { instrumentation.runOnMainSync {
            val themed = ContextThemeWrapper(context, R.style.DarkTheme)
            val root = FrameLayout(themed)
            val nested = FrameLayout(themed)
            val toolbar = Toolbar(themed).apply { inflateMenu(R.menu.top_menu) }
            root.addView(nested); nested.addView(toolbar)
            preferences.edit().putBoolean("hex_whispers", false).putBoolean("hex_moderator_tools", true).commit()
            ChannelToolsMenus.configure(toolbar.menu, preferences)
            val whisper = toolbar.menu.findItem(R.id.hex_whispers)
            assertFalse((0 until toolbar.menu.size()).any { toolbar.menu.getItem(it).title == context.getString(R.string.hex_mod_title) })
            assertFalse(whisper.isVisible)

            preferences.edit().putBoolean("hex_whispers", true).putBoolean("hex_moderator_tools", false).commit()
            ChannelToolsMenus.refresh(root, preferences)
            assertTrue(whisper.isVisible)
            assertSame(toolbar, nested.getChildAt(0)); assertSame(whisper, toolbar.menu.findItem(R.id.hex_whispers))

            preferences.edit().putBoolean("hex_whispers", false).putBoolean("hex_moderator_tools", true).commit()
            ChannelToolsMenus.refresh(root, preferences)
            assertFalse(whisper.isVisible)
        } } finally { preferences.edit().clear().commit() }
    }
}
