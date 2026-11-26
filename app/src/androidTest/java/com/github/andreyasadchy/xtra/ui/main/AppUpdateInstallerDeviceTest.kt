package com.github.andreyasadchy.xtra.ui.main

import android.app.ActivityOptions
import android.content.ComponentName
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AppUpdateInstallerDeviceTest {
    @Suppress("DEPRECATION")
    @Test fun installerCallbackExplicitlyGrantsTheSupportedCreatorLaunchMode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.github.andreyasadchy.xtra.qa", context.packageName)
        val options = AppUpdateInstaller.callbackOptions()
        if (Build.VERSION.SDK_INT >= 34) {
            assertNotNull(options)
            val expected = if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            assertEquals(expected, options!!.pendingIntentCreatorBackgroundActivityStartMode)
        } else assertNull(options)
    }

    @Suppress("DEPRECATION")
    @Test fun callbackIsPrivateMutableAndSeparateForEachInstallerSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.github.andreyasadchy.xtra.qa", context.packageName)
        val activity = context.packageManager.getActivityInfo(ComponentName(context, UpdateInstallActivity::class.java), 0)
        assertFalse(activity.exported)
        val first = AppUpdateInstaller.callback(context, 71001)
        val second = AppUpdateInstaller.callback(context, 71002)
        try {
            assertNotEquals(first, second)
            assertEquals(context.packageName, first.creatorPackage)
            if (Build.VERSION.SDK_INT >= 31) assertFalse(first.isImmutable)
        } finally {
            first.cancel()
            second.cancel()
        }
    }
}
