import ast
import __future__
from pathlib import Path
import re
import sys
from types import SimpleNamespace
import unittest
from urllib.parse import quote, urlparse

PYTHON = Path(__file__).resolve().parents[1] / "app/src/main/python"
sys.path.insert(0, str(PYTHON))
from hexstream_proxy_route import PlaybackProxyRoute, is_playback_metadata


class FakeHTTP:
    def __init__(self):
        self.headers = {}
        self.calls = []

    def request(self, method, url, *args, **kwargs):
        self.calls.append((method, url, kwargs))
        return kwargs


class ProxyRouteTests(unittest.TestCase):
    def test_initial_and_repeated_low_latency_playlists_keep_proxy(self):
        http = FakeHTTP()
        route = PlaybackProxyRoute(http, "fixture.invalid:2023")
        for url in ("https://gql.twitch.tv/gql", "https://usher.ttvnw.net/api/channel/hls/fixture.m3u8",
                    "https://fixture.playlist.live-video.net/v1/playlist/fixture.m3u8?_HLS_msn=1",
                    "https://fixture.playlist.live-video.net/v1/playlist/fixture.m3u8?_HLS_msn=2",
                    "https://video-weaver.fixture.hls.ttvnw.net/quality.m3u8"):
            self.assertEqual(http.request("GET", url)["proxies"]["https"], "http://fixture.invalid:2023")
        route.close()

    def test_video_bytes_and_unrelated_hosts_are_not_proxied(self):
        http = FakeHTTP()
        route = PlaybackProxyRoute(http, "fixture.invalid:2023")
        for url in ("https://video-edge.ttvnw.net/segment.ts", "https://video-edge.ttvnw.net/segment.m4s",
                    "https://gql.twitch.tv.attacker.invalid/gql", "https://attacker.invalid/master.m3u8",
                    "https://notttvnw.net/master.m3u8", "http://usher.ttvnw.net/master.m3u8"):
            self.assertNotIn("proxies", http.request("GET", url))
        route.close()

    def test_closing_restores_original_request_and_is_idempotent(self):
        http = FakeHTTP()
        original = http.request
        route = PlaybackProxyRoute(http, "fixture.invalid:2023")
        route.close()
        route.close()
        self.assertEqual(http.request, original)
        self.assertNotIn("proxies", http.request("GET", "https://usher.ttvnw.net/master.m3u8"))

    def test_channel_sessions_do_not_share_routes(self):
        first, second = FakeHTTP(), FakeHTTP()
        a = PlaybackProxyRoute(first, "first.invalid:2023")
        b = PlaybackProxyRoute(second, "second.invalid:2023")
        url = "https://usher.ttvnw.net/master.m3u8"
        a.close()
        self.assertNotIn("proxies", first.request("GET", url))
        self.assertEqual(second.request("GET", url)["proxies"]["https"], "http://second.invalid:2023")
        b.close()


class PlaylistFallbackTests(unittest.TestCase):
    def service(self, values, lookup, parse):
        parsed = ast.parse((PYTHON / "hexstream_streamlink_twitch.py").read_text(encoding="utf-8"))
        node = next(item for item in parsed.body if isinstance(item, ast.ClassDef) and item.name == "PlaylistProxyService")
        namespace = dict(urlparse=urlparse, re=re, quote=quote, PlaybackProxyRoute=PlaybackProxyRoute,
                         OSError=OSError, PluginError=RuntimeError, NoStreamsError=ValueError,
                         log=SimpleNamespace(info=lambda *a: None, error=lambda *a: None, debug=lambda *a: None),
                         TwitchHLSStream=SimpleNamespace(parse_variant_playlist=parse))
        exec(compile(ast.Module(body=[node], type_ignores=[]), "<production-playlist-proxy>", "exec"), namespace)
        options = {"proxy-playlist": values}
        http = FakeHTTP()
        plugin = SimpleNamespace(get_option=options.get, session=SimpleNamespace(http=http),
                                 api=SimpleNamespace(is_channel_live=lambda channel: True),
                                 _get_hls_streams_live=lambda channel: lookup(http))
        return namespace["PlaylistProxyService"](plugin), plugin, http

    def test_successful_lookup_keeps_route_for_playback_reloads(self):
        service, plugin, http = self.service(["httpproxy://fixture.invalid:2023"],
            lambda http: {"1080p": object()}, lambda *a, **kw: {})
        self.assertTrue(service.streams("fixture"))
        self.assertIn("proxies", http.request("GET", "https://usher.ttvnw.net/live.m3u8"))
        plugin._hexstream_proxy_route.close()

    def test_empty_http_result_tries_next_endpoint_without_leaking_route(self):
        seen = []
        def parse(session, url, **kwargs):
            seen.append(session.http.request("GET", "https://usher.ttvnw.net/live.m3u8"))
            return {"1080p": object()}
        service, plugin, http = self.service(["httpproxy://empty.invalid:2023", "https://next.invalid/[channel]?fixture=1"],
            lambda http: {}, parse)
        self.assertTrue(service.streams("fixture"))
        self.assertEqual(len(seen), 1)
        self.assertNotIn("proxies", seen[0])

    def test_failed_http_lookup_tries_next_endpoint(self):
        def fail(http):
            raise RuntimeError("synthetic unavailable proxy")
        service, plugin, http = self.service(["httpproxy://failed.invalid:2023", "https://next.invalid/[channel]?fixture=1"],
            fail, lambda *a, **kw: {"720p": object()})
        self.assertTrue(service.streams("fixture"))
        self.assertNotIn("proxies", http.request("GET", "https://usher.ttvnw.net/live.m3u8"))

    def test_all_failed_endpoints_do_not_silently_go_direct(self):
        service, plugin, http = self.service(["httpproxy://empty.invalid:2023"],
            lambda http: {}, lambda *a, **kw: {})
        with self.assertRaises(ValueError):
            service.streams("fixture")


class AdOutputTests(unittest.TestCase):
    def test_marked_segments_are_filtered_and_only_state_changes_are_forwarded(self):
        parsed = ast.parse((PYTHON / "hexstream_streamlink_twitch.py").read_text(encoding="utf-8"))
        node = next(item for item in parsed.body if isinstance(item, ast.ClassDef) and item.name == "TwitchHLSStreamWriter")
        states = []
        namespace = {"HLSStreamWriter": object}
        exec(compile(ast.Module(body=[node], type_ignores=[]), "<production-ad-writer>", "exec",
                     flags=__future__.annotations.compiler_flag), namespace)
        writer = namespace["TwitchHLSStreamWriter"]()
        writer.session = SimpleNamespace(options={"hexstream-status": SimpleNamespace(onState=states.append)})
        self.assertTrue(writer.should_filter_segment(SimpleNamespace(ad=True)))
        self.assertTrue(writer.should_filter_segment(SimpleNamespace(ad=True)))
        self.assertFalse(writer.should_filter_segment(SimpleNamespace(ad=False)))
        self.assertFalse(writer.should_filter_segment(SimpleNamespace(ad=False)))
        self.assertEqual(["waiting", "ready"], states)


if __name__ == "__main__":
    unittest.main()
