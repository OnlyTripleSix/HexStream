package com.github.andreyasadchy.xtra.ui.main

internal object UpdateInstallSessions {
    fun superseded(target: String?, ownPackage: String, createdMillis: Long, installedUpdateMillis: Long): Boolean =
        target == ownPackage && createdMillis > 0 && createdMillis < installedUpdateMillis
}
