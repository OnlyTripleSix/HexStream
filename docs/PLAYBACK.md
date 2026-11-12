# Playback, proxies, accounts, points and zoom

## Global playback presets

The custom playback proxy list is stored once in a global database. It is seeded only when the global list is first created and empty. Existing account-specific custom URLs are imported once without deleting account data or overwriting the global list. Switching accounts does not reseed, reorder or clear it.

| Initial order | Preset | URL |
| --- | --- | --- |
| 1 | EU2 Luminous | `https://eu2.luminous.dev/live/$channel?allow_source=true&allow_audio_only=true&fast_bread=true` |
| 2 | EU Luminous | `https://eu.luminous.dev/live/$channel?allow_source=true&allow_audio_only=true&fast_bread=true` |
| 3 | PerfProd North America | `https://lb-na.cdn-perfprod.com/live/$channel?allow_source=true&allow_audio_only=true&fast_bread=true` |
| 4 | PerfProd Europe | `https://lb-eu.cdn-perfprod.com/live/$channel?allow_source=true&allow_audio_only=true&fast_bread=true` |
| 5 | PerfProd Europe 2 | `https://lb-eu2.cdn-perfprod.com/live/$channel?allow_source=true&allow_audio_only=true&fast_bread=true` |
| 6 | PerfProd Asia | `https://lb-as.cdn-perfprod.com/live/$channel?allow_source=true&allow_audio_only=true&fast_bread=true` |

`$channel` is replaced with the stream’s channel login. Edit, reorder, enable/disable or add custom entries through the existing playback proxy settings. Deleting a preset is respected; account switching does not restore it.

## Keep the proxy choice honest

With **Playback URL proxies** enabled, HexStream tries your enabled endpoints in the saved order. It stays with the first working endpoint. A transport/playlist failure or detected Twitch ad marker stops that source and tries the next endpoint; a pass never cycles endlessly through the same list.

When the list runs out, playback stops and displays **Retry playback**. Retry reloads your current enabled list from the beginning. Check **Settings → Playback proxy URLs** to enable or reorder entries. Custom URLs, disabled entries and the six initial Luminous/PerfProd presets are preserved. Changing Twitch accounts changes none of these global choices.

**Allow direct playback if proxies fail** is a separate global option, initially off. Turn it on only if you want direct Twitch playback after the proxy list fails; Twitch may then show ads. The app warns when it takes that route. Disabling playback URL proxies deliberately selects the inherited direct/HTTP/video-swap path. HTTP host/port entries and their recovery behavior remain separate.

## What ad recovery can detect

Ad-marker recovery uses the default **ExoPlayer** engine. Android's optional **MediaPlayer** does not expose parsed live playlists, so it supports the same explicit proxy-exhaustion choice and Retry screen but cannot provide this marker-based ad recovery. Pinch zoom works with either engine.

The live player checks Twitch's retained `EXT-X-DATERANGE` tags, including stitched-ad IDs/classes and Twitch ad attributes, plus known ad segment titles and compatible parsed interstitials. Date ranges are matched against the live playlist tail, including low-latency parts. Updates for the same range ID are combined; an explicit end overrides its planned duration. Unrelated program markers are ignored.

Media3 retains raw tags but its interstitial parser recognizes Apple's interstitial class. Reading only that parsed list can miss Twitch's class. The added detector reads the retained tags without rewriting playlists or downloading thumbnails. [Media3 parser source](https://github.com/androidx/media/blob/release/libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/playlist/HlsPlaylistParser.java).

This does not guarantee that every ad is recognizable or that a public proxy always has a clean source. Unmarked ads and a streamer's own promotions cannot be reliably identified here. A playlist/status response is not proof of hours of ad-free playback. Proxy availability and Twitch's responses can change. The app does not send your account token or cookies in these diagnostics, and new recovery logs contain no credentials or playlist URLs.

Proxy settings and pagination have separate implementations and state. Independent operators control availability, content and delay. A proxy necessarily receives playback requests and network information; review [privacy](PRIVACY.md) before adding an endpoint you do not trust.

## Live delay

Low-delay mode is enabled by default for live ExoPlayer playback. The target live offset is **1,000 ms**, with bounded playback-speed adjustment from **0.97× to 1.08×**. Live startup/rebuffer thresholds are **500 ms / 1,000 ms**; player-speed changes are accounted for. VOD and disabled low-delay paths keep their normal buffering behavior.

This target measures the player’s position relative to the stream’s available live edge. It does not mean that the phone is guaranteed one second behind the broadcaster or another device. Twitch/proxy playlist generation, stream encoding, network conditions and buffering all contribute to end-to-end delay. Tap LIVE to return to the current edge if playback falls behind; turn off low-delay mode if your connection needs more buffering.

The custom `LiveLoadControl` explicitly forwards the Media3 `LoadControl` contract, including Java default methods. A compatibility test checks delegation when the Media3 API changes.

## Gestures

With the player controls hidden, vertical swipes in the left quarter adjust window brightness; swipes in the right quarter adjust media volume. Each control can be enabled separately. A 24 dp exclusion near the system edges and multi-touch handling reduce conflicts with navigation.

The existing center drag/minimize and miniplayer interactions remain available. Temporary window brightness is restored when leaving the player. Gestures operate on playback UI state and do not affect filters or account selection.

## Pinch to inspect the game

**Player settings → Pinch to zoom video** starts enabled. Spread two fingers over the video to enlarge it from **100% to 400%**. Move both fingers together to pan the enlarged picture. Movement is bounded so you cannot drag the enlarged video entirely away.

Pinch inward to return to 100%, or tap **Aspect ratio** while zoomed to reset. That first reset does not change your saved aspect ratio; the button resumes its usual aspect-ratio cycle at 100%. Chat, subtitles, buttons and the miniplayer container are not enlarged.

One finger still adjusts brightness/volume or uses the existing center minimize gesture. A pinch owns its remaining touch sequence after one finger lifts, so its tail cannot accidentally minimize the stream. Zoom is unavailable in the miniplayer, picture-in-picture, audio-only and chat-only modes. It resets when layout changes, the player stops, or you minimize/enter PiP. It creates no second player or chat connection.

## Saved accounts and notifications

Multiple Twitch accounts can be saved, with one active at a time. Saved credentials use an Android Keystore-backed AES-GCM vault. Account databases/settings remain separate from the global content-filter and proxy stores.

The followed-channel exception uses follows belonging to the current active account, including available locally followed creators. Switching accounts updates that context; it does not merge all saved accounts’ follows.

The background notification worker checks the active account before processing, before individual notifications and before the summary. There is no cross-account aggregate notification mode. System notification permissions and Twitch/network availability still determine whether a notification can be delivered.

## Bonus chest and channel rewards

The Points panel can show the current balance, an available periodic bonus claim and the stream’s custom channel rewards. It checks the active account and current channel before making a request. Reward rows show their cost/availability; a reward requiring text input collects it before final confirmation.

Claiming a chest and spending points are different actions. Spending requires an explicit confirmation. A submission gate prevents repeated taps or a second confirmation from creating duplicate requests. Mutating operations are not automatically retried on an ambiguous failure; success requires a confirmed response, including a redemption identity for rewards. Read-only balance/reward loads can be retried.

Xtra’s existing automatic bonus preference remains available. Reward availability and Twitch’s internal GraphQL behavior can change. Real point spending is not part of unattended regression testing.
