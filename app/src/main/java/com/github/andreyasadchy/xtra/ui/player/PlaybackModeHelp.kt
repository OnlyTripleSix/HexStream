package com.github.andreyasadchy.xtra.ui.player

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object PlaybackModeHelp {
    fun showSelector(activity: Activity, selected: Int, onSelected: (Int) -> Unit): AlertDialog =
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.hex_playback_source)
            .setSingleChoiceItems(R.array.hexPlaybackModeEntries, selected) { dialog, which ->
                onSelected(which)
                dialog.dismiss()
            }
            .setNeutralButton(R.string.hex_modes_help) { _, _ -> show(activity) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

    fun show(activity: Activity): AlertDialog = MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.hex_modes_help)
        .setMessage(R.string.hex_modes_help_text)
        .setPositiveButton(R.string.hex_open_proxy_list) { _, _ ->
            if (activity is SettingsActivity) activity.openPlaybackProxyList()
            else activity.startActivity(Intent(activity, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_OPEN_PLAYBACK_PROXIES, true))
        }
        .setNegativeButton(android.R.string.ok, null)
        .show()
}
