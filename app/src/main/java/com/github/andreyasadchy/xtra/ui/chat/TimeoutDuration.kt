package com.github.andreyasadchy.xtra.ui.chat

internal object TimeoutDuration {
    const val DEFAULT_SECONDS = 600
    const val MAX_SECONDS = 1_209_600

    /** Helix needs seconds. A missing argument keeps Twitch's ten-minute default. */
    fun helixSeconds(input: String?): Int? {
        if (input == null) return DEFAULT_SECONDS
        val value = input.trim()
        val suffix = value.lastOrNull()?.takeIf { it.isLetter() }
        val multiplier = when (suffix?.lowercaseChar()) {
            null, 's' -> 1
            'm' -> 60
            'h' -> 3600
            'd' -> 86400
            else -> return null
        }
        val digits = if (suffix == null) value else value.dropLast(1)
        if (digits.isEmpty() || digits.any { it !in '0'..'9' }) return null
        val amount = digits.toIntOrNull() ?: return null
        // Bound before multiplication so large inputs cannot wrap into another duration.
        if (amount !in 1..(MAX_SECONDS / multiplier)) return null
        return amount * multiplier
    }
}
