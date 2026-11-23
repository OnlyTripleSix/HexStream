from __future__ import annotations

import logging
import re

from streamlink import Streamlink
from streamlink.exceptions import NoStreamsError
from streamlink.session.http import Urllib3UtilUrlPercentReOverride

from hexstream_streamlink_twitch import Twitch, TwitchHLSStream, log as logger


if not hasattr(Urllib3UtilUrlPercentReOverride, "sub"):
    # Streamlink 8.3.0 implements subn only. urllib3 2.8 also calls sub.
    # Delegate that operation to the original regex as in Streamlink 8.6.3.
    Urllib3UtilUrlPercentReOverride.sub = Urllib3UtilUrlPercentReOverride._re_percent_encoding.sub


class PlaybackLog(logging.Handler):
    """Forward just the coarse ad events, never URLs or session data."""

    def emit(self, record):
        message = str(record.msg).lower()
        if message.startswith("detected advertisement break"):
            print("HexStreamStreamlink ad_break_detected", flush=True)
        elif message.startswith("waiting for pre-roll ads"):
            print("HexStreamStreamlink waiting_for_preroll", flush=True)


logger.addHandler(PlaybackLog())
logger.setLevel(logging.INFO)
logger.propagate = False


class StreamReader:
    def __init__(self, session, stream, quality):
        self._session = session
        self._stream = stream
        self.quality = quality

    def read(self, size):
        return self._stream.read(size)

    def close(self):
        try:
            self._stream.close()
        finally:
            self._session.http.close()


def pick_stream(streams, requested_quality):
    # These streams contain audio and video in one rendition. Other Streamlink
    # stream types can launch FFmpeg, which is outside this Android playback path.
    available = {key: stream for key, stream in streams.items()
                 if isinstance(stream, TwitchHLSStream) and key not in ("best", "worst", "audio_only")}
    if not available:
        raise NoStreamsError
    requested = (requested_quality or "").lower().split(" ")[0]
    if requested in available:
        return requested, available[requested]
    match = re.fullmatch(r"(\d+)p(\d+)?", requested)
    if match:
        # Use the nearest available rendition below the user's chosen height.
        target_height = int(match[1])
        nearby = [key for key in available
                  if (size := re.match(r"(\d+)p", key)) and int(size[1]) <= target_height]
        if nearby:
            name = max(nearby, key=Twitch.stream_weight)
            return name, available[name]
    name = max(available, key=Twitch.stream_weight)
    return name, available[name]


def open_stream(channel, requested_quality, proxies, allow_direct):
    if not re.fullmatch(r"[A-Za-z0-9_]{1,25}", channel):
        raise ValueError("Invalid channel login")
    proxies = [str(proxy) for proxy in proxies]
    if not proxies and not allow_direct:
        raise NoStreamsError
    session = Streamlink({
        "http-timeout": 6,
        "hls-live-edge": 2,
        "stream-segment-timeout": 8,
        "stream-timeout": 110,
    })
    try:
        plugin = Twitch(session, f"https://www.twitch.tv/{channel}", options={
            "disable-ads": True,
            "low-latency": True,
            "supported-codecs": ["h264"],
            "proxy-playlist": proxies,
            "proxy-playlist-fallback": bool(allow_direct),
        })
        name, stream = pick_stream(plugin.streams(), requested_quality)
        return StreamReader(session, stream.open(), name)
    except BaseException:
        session.http.close()
        raise
