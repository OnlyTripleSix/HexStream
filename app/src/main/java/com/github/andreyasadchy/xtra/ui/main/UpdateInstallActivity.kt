package com.github.andreyasadchy.xtra.ui.main

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.tokenPrefs

/** Private destination for the installer-owned PendingIntent; never accepts public deep links. */
class UpdateInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE) ==
            PackageInstaller.STATUS_PENDING_USER_ACTION
        ) {
            val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            }
            if (confirmation == null) AppUpdateInstaller.showFailure(this)
            confirmation?.let {
                tokenPrefs().edit { putLong(C.UPDATE_LAST_CHECKED, System.currentTimeMillis()) }
                startActivity(it)
            }
        } else if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE) != PackageInstaller.STATUS_SUCCESS) {
            AppUpdateInstaller.showFailure(this)
        }
        finish()
    }
}
