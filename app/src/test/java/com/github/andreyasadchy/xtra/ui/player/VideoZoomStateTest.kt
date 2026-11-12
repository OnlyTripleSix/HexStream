package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.*
import org.junit.Test

class VideoZoomStateTest {
    private fun VideoZoomState.move(factor: Float, x: Float = 500f, y: Float = 300f, previousX: Float = 500f, previousY: Float = 300f) =
        move(factor, x, y, previousX, previousY, 1000f, 600f, 1000f, 600f)

    @Test fun centersScaleAndClampsFromFitToFourTimes() {
        val zoom = VideoZoomState()
        zoom.move(2f)
        assertEquals(2f, zoom.scale, 0f); assertEquals(0f, zoom.x, 0f)
        zoom.move(100f); assertEquals(4f, zoom.scale, 0f)
        zoom.move(.01f); assertEquals(1f, zoom.scale, 0f)
        assertEquals(0f, zoom.x, 0f); assertEquals(0f, zoom.y, 0f)
    }
    @Test fun keepsOffCenterPinchAnchorAndSupportsTwoFingerPan() {
        val zoom = VideoZoomState()
        zoom.move(2f, 750f, 400f, 750f, 400f)
        assertEquals(-250f, zoom.x, .001f); assertEquals(-100f, zoom.y, .001f)
        zoom.move(1f, 800f, 450f, 750f, 400f)
        assertEquals(-200f, zoom.x, .001f); assertEquals(-50f, zoom.y, .001f)
    }
    @Test fun panCannotExposeSpaceOutsideEnlargedVideo() {
        val zoom = VideoZoomState()
        zoom.move(2f)
        zoom.move(1f, 10000f, -10000f)
        assertEquals(500f, zoom.x, 0f); assertEquals(-300f, zoom.y, 0f)
    }
    @Test fun letterboxedContentStaysCenteredUntilItFillsViewport() {
        val zoom = VideoZoomState()
        zoom.move(1.2f, 900f, 600f, 500f, 300f, 600f, 400f, 1000f, 600f)
        assertEquals(0f, zoom.x, 0f); assertEquals(0f, zoom.y, 0f)
    }
    @Test fun invalidInputCannotPoisonLaterGestures() {
        val zoom = VideoZoomState()
        for (factor in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f)) zoom.move(factor)
        zoom.move(2f, Float.NaN)
        assertEquals(1f, zoom.scale, 0f)
        zoom.move(2f); assertEquals(2f, zoom.scale, 0f)
    }
    @Test fun resetClearsZoomAndPanTogether() {
        val zoom = VideoZoomState()
        zoom.move(3f, 700f, 400f)
        zoom.reset()
        assertEquals(1f, zoom.scale, 0f); assertEquals(0f, zoom.x, 0f); assertEquals(0f, zoom.y, 0f)
    }
}
