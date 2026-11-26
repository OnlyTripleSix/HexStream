package com.github.andreyasadchy.xtra.ui.main

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import com.github.andreyasadchy.xtra.R

/** Only the installer receives this explicit, session-specific callback. */
internal object AppUpdateInstaller {
    fun ensurePermission(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()) return true
        Toast.makeText(activity, R.string.update_install_permission, Toast.LENGTH_LONG).show()
        try {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${activity.packageName}".toUri()))
        } catch (_: android.content.ActivityNotFoundException) {
            showFailure(activity)
        }
        return false
    }

    @Suppress("DEPRECATION")
    internal fun callbackOptions(): Bundle? = if (Build.VERSION.SDK_INT >= 34) {
        ActivityOptions.makeBasic().setPendingIntentCreatorBackgroundActivityStartMode(
            if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
            else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        ).toBundle()
    } else null

    internal fun callback(context: Context, sessionId: Int): PendingIntent = PendingIntent.getActivity(
        context,
        sessionId,
        Intent(context, UpdateInstallActivity::class.java).setAction(MainActivity.INTENT_INSTALL_UPDATE),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        callbackOptions()
    )

    fun install(context: Context, apk: ByteArray) {
        require(apk.isNotEmpty())
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("package", 0, apk.size.toLong()).use { output ->
                    output.write(apk)
                    session.fsync(output)
                }
                session.commit(callback(context, sessionId).intentSender)
            }
        } catch (failure: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw failure
        }
    }

    fun showFailure(context: Context) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_LONG).show()
        }
    }
}
