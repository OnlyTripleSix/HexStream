package com.github.andreyasadchy.xtra.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.prefs
import kotlin.math.abs
import kotlin.math.roundToInt

internal class PlayerSwipeControls(private val activity: Activity, private val overlay: TextView) {
    private val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val density = activity.resources.displayMetrics.density
    private var candidate: SwipeControlPolicy.Control? = null
    private var claimed = false
    private var startX = 0f
    private var startY = 0f
    private var startLevel = 0f
    private var originalBrightness: Float? = null
    private val hide = Runnable { overlay.visibility = View.GONE }

    fun touch(event: MotionEvent, width: Int, height: Int, enabled: Boolean, cancelOriginal: () -> Unit): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                claimed = false
                candidate = if (enabled) SwipeControlPolicy.control(event.x, event.y, width.toFloat(), height.toFloat(),
                    24f * density, activity.prefs().getBoolean("hex_swipe_brightness", true),
                    activity.prefs().getBoolean("hex_swipe_volume", true)) else null
                startX = event.x
                startY = event.y
                startLevel = if (candidate == SwipeControlPolicy.Control.BRIGHTNESS) {
                    activity.window.attributes.screenBrightness.takeIf { it >= 0f }
                        ?: (Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
                } else {
                    audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (claimed && candidate == null) return true
                val control = candidate ?: return false
                val dy = event.y - startY
                val dx = event.x - startX
                if (!claimed) {
                    if (abs(dx) > 24f * density && !SwipeControlPolicy.vertical(dx, dy, 24f * density)) {
                        candidate = null
                        return false
                    }
                    if (!SwipeControlPolicy.vertical(dx, dy, 24f * density)) return true
                    claimed = true
                    cancelOriginal()
                }
                val level = SwipeControlPolicy.level(startLevel, dy, height * .7f)
                val label = if (control == SwipeControlPolicy.Control.BRIGHTNESS) {
                    if (originalBrightness == null) originalBrightness = activity.window.attributes.screenBrightness
                    activity.window.attributes = activity.window.attributes.apply { screenBrightness = level.coerceAtLeast(.02f) }
                    R.string.hex_brightness
                } else {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                        (level * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(), 0)
                    R.string.hex_volume
                }
                overlay.removeCallbacks(hide)
                overlay.text = activity.getString(R.string.hex_control_level, activity.getString(label), (level * 100).roundToInt())
                overlay.visibility = View.VISIBLE
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val consumed = claimed
                candidate = null
                // Keep consuming an already claimed gesture until release; do not hand it to minimize.
                if (consumed) return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val consumed = claimed
                candidate = null
                claimed = false
                overlay.postDelayed(hide, 700)
                return consumed
            }
        }
        return claimed
    }

    fun cancelGesture() {
        candidate = null
        claimed = false
        overlay.removeCallbacks(hide)
        overlay.visibility = View.GONE
    }

    fun restore() {
        originalBrightness?.let { previous ->
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = previous }
        }
        originalBrightness = null
        cancelGesture()
    }
}
