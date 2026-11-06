package com.github.andreyasadchy.xtra.ui.main

import android.content.SharedPreferences
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import com.github.andreyasadchy.xtra.R

internal object ChannelToolsMenus {
    fun configure(menu: Menu, preferences: SharedPreferences) {
        menu.findItem(R.id.hex_whispers)?.isVisible = preferences.getBoolean("hex_whispers", false)
    }

    /** Apply settings on return without replacing fragments, paging sessions or the player. */
    fun refresh(root: View, preferences: SharedPreferences) {
        if (root is Toolbar) configure(root.menu, preferences)
        if (root is ViewGroup) for (index in 0 until root.childCount) refresh(root.getChildAt(index), preferences)
    }
}
