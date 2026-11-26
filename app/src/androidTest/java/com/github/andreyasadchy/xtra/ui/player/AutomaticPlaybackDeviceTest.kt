package com.github.andreyasadchy.xtra.ui.player

import androidx.test.platform.app.InstrumentationRegistry
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.junit.Assert.*
import org.junit.Test

class AutomaticPlaybackDeviceTest {
    @Test fun bundledPythonCanCallTheKeptStatusBridgeWithoutNetworkRequests() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Use the isolated QA package", "com.github.andreyasadchy.xtra.qa", context.packageName)
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        val states = mutableListOf<String>()
        val callback = StreamlinkStatus { states.add(it) }
        val method = Python.getInstance().getModule("builtins").callAttr("getattr", callback, "onState")
        method.call("waiting")
        method.call("ready")
        method.call("unrecognized")
        assertEquals(listOf("waiting", "ready"), states)
    }

    @Test fun bundledRouteRecognizesPlaylistRefreshesAndExcludesVideoBytes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Use the isolated QA package", "com.github.andreyasadchy.xtra.qa", context.packageName)
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        val module = Python.getInstance().getModule("hexstream_proxy_route")
        assertTrue(module.callAttr("is_playback_metadata", "https://fixture.playlist.live-video.net/live.m3u8?_HLS_msn=2").toBoolean())
        assertFalse(module.callAttr("is_playback_metadata", "https://fixture.live-video.net/segment.ts").toBoolean())
    }
}
