package com.github.andreyasadchy.xtra.ui.player

import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.TextView
import com.github.andreyasadchy.xtra.R
import kotlin.math.roundToInt

internal class PlayerZoomControls(private val video: View, private val viewport: View,
                                  private val overlay: TextView, private val changed: (Boolean) -> Unit = {}) {
    private val state = VideoZoomState()
    private var claimed = false
    private var factor = 1f
    private var focusX = 0f
    private var focusY = 0f
    private val hide = Runnable { overlay.visibility = View.GONE }
    private val detector = ScaleGestureDetector(video.context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean { factor = detector.scaleFactor; return true }
    }).apply { isQuickScaleEnabled = false }
    private val layoutListener = View.OnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
        if (r - l != oldR - oldL || b - t != oldB - oldT) reset()
    }
    init { video.addOnLayoutChangeListener(layoutListener) }
    val zoomed get() = state.scale > 1f

    fun touch(event: MotionEvent, enabled: Boolean, onClaim: () -> Unit): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) claimed = false
        if (!enabled && !claimed) return false
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && enabled && !claimed) {
            claimed = true
            onClaim()
        }
        factor = 1f
        detector.onTouchEvent(event)
        if (claimed) {
            if (event.pointerCount >= 2 && event.actionMasked == MotionEvent.ACTION_MOVE) {
                val (x, y) = focus(event)
                state.move(factor, x, y, focusX, focusY, video.width.toFloat(), video.height.toFloat(),
                    viewport.width.toFloat(), viewport.height.toFloat())
                focusX = x; focusY = y
                apply()
                overlay.removeCallbacks(hide)
                overlay.text = video.context.getString(R.string.hex_zoom_level, (state.scale * 100).roundToInt())
                overlay.visibility = View.VISIBLE
            } else if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
                val (x, y) = focus(event, if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1)
                focusX = x; focusY = y
            }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                claimed = false
                overlay.postDelayed(hide, 700)
            }
            // Own the tail after either finger lifts, so it cannot become a minimize swipe/tap.
            return true
        }
        return false
    }

    private fun focus(event: MotionEvent, excluded: Int = -1): Pair<Float, Float> {
        var x = 0f; var y = 0f; var count = 0
        for (index in 0 until event.pointerCount) if (index != excluded) {
            x += event.getX(index); y += event.getY(index); count++
        }
        return x / count.coerceAtLeast(1) to y / count.coerceAtLeast(1)
    }

    private fun apply() {
        video.pivotX = video.width / 2f; video.pivotY = video.height / 2f
        video.scaleX = state.scale; video.scaleY = state.scale
        video.translationX = state.x; video.translationY = state.y
        changed(zoomed)
    }

    fun reset() {
        state.reset(); claimed = false; apply()
        overlay.removeCallbacks(hide); overlay.visibility = View.GONE
    }

    fun dispose() { reset(); video.removeOnLayoutChangeListener(layoutListener) }
}
