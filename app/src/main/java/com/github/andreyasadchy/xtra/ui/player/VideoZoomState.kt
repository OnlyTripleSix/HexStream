package com.github.andreyasadchy.xtra.ui.player

import kotlin.math.max

/** Video-only transform. Single-finger gestures remain owned by the player. */
internal class VideoZoomState {
    var scale = 1f
        private set
    var x = 0f
        private set
    var y = 0f
        private set

    fun move(factor: Float, focusX: Float, focusY: Float, previousX: Float, previousY: Float,
             contentWidth: Float, contentHeight: Float, viewportWidth: Float, viewportHeight: Float) {
        if (!factor.isFinite() || factor <= 0f || listOf(focusX, focusY, previousX, previousY,
                contentWidth, contentHeight, viewportWidth, viewportHeight).any { !it.isFinite() }) return
        if (contentWidth <= 0f || contentHeight <= 0f || viewportWidth <= 0f || viewportHeight <= 0f) return
        val next = (scale * factor).coerceIn(1f, 4f)
        val ratio = next / scale
        val limitX = max(0f, (contentWidth * next - viewportWidth) / 2f)
        val limitY = max(0f, (contentHeight * next - viewportHeight) / 2f)
        x = (focusX - viewportWidth / 2f - ratio * (previousX - viewportWidth / 2f - x)).coerceIn(-limitX, limitX)
        y = (focusY - viewportHeight / 2f - ratio * (previousY - viewportHeight / 2f - y)).coerceIn(-limitY, limitY)
        scale = next
        if (scale == 1f) reset()
    }

    fun reset() { scale = 1f; x = 0f; y = 0f }
}
