package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.model.ui.CustomProxy
import com.github.andreyasadchy.xtra.model.ui.StreamProxy
import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class StreamlinkProxyRoutesTest {
    @Test fun httpRoutesComeFirstAndEachListKeepsItsEnabledOrder() {
        val routes = StreamlinkProxyRoutes.urls(listOf(
            CustomProxy("https://second.example.test/live/$" + "channel", false, 2, true),
            CustomProxy("https://first.example.test/live/$" + "channel", false, 1, true),
            CustomProxy("https://disabled.example.test", false, 0, false),
        ), listOf(
            StreamProxy(host = "second.example.test", port = 2023, position = 2),
            StreamProxy(host = "disabled.example.test", port = 2023, position = 0, enabled = false),
            StreamProxy(host = "first.example.test", port = 2023, position = 1),
        ))
        assertEquals(listOf("httpproxy://first.example.test:2023", "httpproxy://second.example.test:2023",
            "https://first.example.test/live/[channel]", "https://second.example.test/live/[channel]"), routes)
    }

    @Test fun invalidAndDuplicateRoutesDoNotConsumeAttempts() {
        val routes = StreamlinkProxyRoutes.urls(listOf(
            CustomProxy("file:///private/file", false, 0, true),
            CustomProxy("https://valid.example.test/live", false, 1, true),
            CustomProxy("https://valid.example.test/live", false, 2, true),
            CustomProxy("https://bad.example.test:99999/live", false, 3, true),
        ), listOf(StreamProxy(host = "bad host", port = 2023, position = 0),
            StreamProxy(host = "valid.example.test", port = -1, position = 1)))
        assertEquals(listOf("https://valid.example.test/live"), routes)
    }

    @Test fun customHttpTunnelsAndEncodedAuthenticationAreRetained() {
        val routes = StreamlinkProxyRoutes.urls(listOf(
            CustomProxy("httpproxy://custom.example.test:2023", false, 1, true),
        ), listOf(StreamProxy(host = "private.example.test", port = 2023,
            username = "fixture", password = "p@ss/word", position = 0)))
        val authenticated = URI(routes.last())
        assertEquals("private.example.test", authenticated.host)
        assertEquals("fixture:p@ss/word", authenticated.userInfo)
        assertEquals("httpproxy://custom.example.test:2023", routes.first())
    }
}
