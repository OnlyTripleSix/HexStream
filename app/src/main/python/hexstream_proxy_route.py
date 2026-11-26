"""Keep a chosen HTTP route for playback metadata, without proxying video bytes."""

from urllib.parse import urlsplit


def is_playback_metadata(url):
    parsed = urlsplit(url)
    host = (parsed.hostname or "").lower()
    if parsed.scheme != "https":
        return False
    if host == "gql.twitch.tv" and parsed.path.rstrip("/") == "/gql":
        return True
    twitch_cdn = any(host == suffix or host.endswith("." + suffix)
                     for suffix in ("ttvnw.net", "live-video.net"))
    return twitch_cdn and parsed.path.lower().endswith(".m3u8")


class PlaybackProxyRoute:
    def __init__(self, http, address):
        self.http = http
        self.original = http.request
        self.proxies = {"http": "http://" + address, "https": "http://" + address}
        self.closed = False

        def request(method, url, *args, **kwargs):
            if is_playback_metadata(url):
                kwargs["proxies"] = dict(self.proxies)
            return self.original(method, url, *args, **kwargs)

        self.request = request
        http.request = request

    def close(self):
        if not self.closed:
            self.closed = True
            if self.http.request is self.request:
                self.http.request = self.original
