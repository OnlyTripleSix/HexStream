package com.github.andreyasadchy.xtra.ui.player

internal object AutomaticPlayback {
    const val KEY = "hex_playback_mode"
    const val AUTOMATIC = "automatic"
    const val STANDARD = "standard"

    fun enabled(value: String?) = value != STANDARD
    fun allowDirect(automatic: Boolean, optedIn: Boolean) = !automatic && optedIn

    class Recovery {
        private var attempts = 0
        fun nextDelayMs(playRequested: Boolean = true): Long? =
            if (playRequested) delays.getOrNull(attempts)?.also { attempts++ } else null
        fun reset() { attempts = 0 }
    }

    private val delays = longArrayOf(2_000, 5_000, 10_000)
}
