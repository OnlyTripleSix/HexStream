package com.github.andreyasadchy.xtra.ui.player

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import org.junit.Assert.*
import org.junit.Test

class PlayerZoomDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun fixture(block: (FrameLayout, FrameLayout, TextView, PlayerZoomControls) -> Unit) {
        val context = instrumentation.targetContext
        assertEquals("Use only the isolated QA package", "com.github.andreyasadchy.xtra.qa", context.packageName)
        val activity = instrumentation.startActivitySync(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SettingsActivity
        lateinit var host: FrameLayout
        lateinit var video: FrameLayout
        lateinit var controls: TextView
        lateinit var zoom: PlayerZoomControls
        var cleanup: (() -> Unit)? = null
        try {
            instrumentation.runOnMainSync {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                host = FrameLayout(activity)
                video = FrameLayout(activity).apply { addView(SurfaceView(activity), FrameLayout.LayoutParams(-1, -1)) }
                controls = TextView(activity).apply { text = "QA controls and chat stay unchanged" }
                val level = TextView(activity)
                host.addView(video, FrameLayout.LayoutParams(-1, -1))
                host.addView(controls, FrameLayout.LayoutParams(-1, 100))
                host.addView(level, FrameLayout.LayoutParams(-2, -2))
                activity.setContentView(host)
                zoom = PlayerZoomControls(video, host, level)
                cleanup = { zoom.dispose() }
            }
            val deadline = SystemClock.uptimeMillis() + 5000
            while (!host.isLaidOut || host.width == 0 || host.height == 0) {
                assertTrue("Zoom fixture must be measured", SystemClock.uptimeMillis() < deadline)
                SystemClock.sleep(30)
            }
            instrumentation.runOnMainSync { block(host, video, controls, zoom) }
        } finally { instrumentation.runOnMainSync { cleanup?.invoke(); activity.finish() } }
    }

    private fun event(action: Int, points: List<Pair<Float, Float>>, time: Long): MotionEvent {
        val properties = points.indices.map { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
        val coordinates = points.map { (x, y) -> MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f } }.toTypedArray()
        return MotionEvent.obtain(100, time, action, points.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    private fun send(zoom: PlayerZoomControls, action: Int, points: List<Pair<Float, Float>>, time: Long, enabled: Boolean = true, claim: () -> Unit = {}): Boolean =
        event(action, points, time).let { try { zoom.touch(it, enabled, claim) } finally { it.recycle() } }

    private fun pinch(host: FrameLayout, zoom: PlayerZoomControls, claim: () -> Unit = {}) {
        val x = host.width / 2f; val y = host.height / 2f
        assertFalse(send(zoom, MotionEvent.ACTION_DOWN, listOf(x - 140 to y), 100, claim = claim))
        assertTrue(send(zoom, MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), listOf(x - 140 to y, x + 140 to y), 120, claim = claim))
        for (step in 1..6) assertTrue(send(zoom, MotionEvent.ACTION_MOVE,
            listOf(x - 140 - step * 40 to y, x + 140 + step * 40 to y), 120L + step * 20, claim = claim))
    }

    @Test fun pinchTransformsVideoSurfaceParentWithoutScalingControlsOrHost() = fixture { host, video, controls, zoom ->
        pinch(host, zoom)
        assertTrue("Pinch must enlarge video", video.scaleX > 1f)
        assertEquals(video.scaleX, video.scaleY, 0f)
        assertTrue(video.getChildAt(0) is SurfaceView)
        assertEquals(1f, host.scaleX, 0f); assertEquals(1f, controls.scaleX, 0f)
        assertTrue(host.clipChildren)
    }

    @Test fun consumesPinchTailAndLeavesNextSingleFingerGestureAvailable() = fixture { host, _, _, zoom ->
        var claims = 0
        pinch(host, zoom) { claims++ }
        assertEquals(1, claims)
        assertTrue(send(zoom, MotionEvent.ACTION_POINTER_UP or (1 shl 8), listOf(100f to 300f, 500f to 300f), 300))
        assertTrue(send(zoom, MotionEvent.ACTION_MOVE, listOf(100f to 900f), 320))
        assertTrue(send(zoom, MotionEvent.ACTION_UP, listOf(100f to 900f), 340))
        assertFalse(send(zoom, MotionEvent.ACTION_DOWN, listOf(100f to 300f), 400))
        assertFalse(send(zoom, MotionEvent.ACTION_MOVE, listOf(100f to 900f), 420))
        assertFalse(send(zoom, MotionEvent.ACTION_UP, listOf(100f to 900f), 440))
    }

    @Test fun disabledZoomDoesNotClaimMinimizedOrPictureInPictureGestures() = fixture { _, video, _, zoom ->
        assertFalse(send(zoom, MotionEvent.ACTION_DOWN, listOf(100f to 300f), 100, false))
        assertFalse(send(zoom, MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), listOf(100f to 300f, 500f to 300f), 120, false))
        assertFalse(send(zoom, MotionEvent.ACTION_MOVE, listOf(0f to 300f, 800f to 300f), 140, false))
        assertEquals(1f, video.scaleX, 0f)
    }

    @Test fun resetRestoresVideoAndDropsAnInterruptedPinch() = fixture { host, video, controls, zoom ->
        pinch(host, zoom)
        zoom.reset()
        assertFalse(zoom.zoomed)
        assertEquals(1f, video.scaleX, 0f); assertEquals(0f, video.translationX, 0f); assertEquals(0f, video.translationY, 0f)
        assertEquals(1f, controls.scaleX, 0f)
        assertFalse(send(zoom, MotionEvent.ACTION_DOWN, listOf(100f to 300f), 400))
    }
}
