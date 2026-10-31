# Changelog

## 1.0.0-beta.8.2 (test candidate)

- Detect the active account's moderator/broadcaster role before showing live-player or chat-message moderation controls.
- Remove free-form channel entry. The dashboard lists channels returned by Twitch and the account's own channel; context tools stay bound to the stream that opened them.
- Verify role again before every native moderation write, block unverified/revoked/other-channel access, and keep AutoMod events bound to the selected broadcaster.
- Clarify that the optional moderator setting controls visibility. Retain the immediate Whispers menu refresh.

## 1.0.0-beta.8.1 (test candidate)

- Refresh the existing browsing toolbar when returning from Settings so enabling Whispers immediately reveals its menu. Apply the same behavior to the moderator-tools toggle.
- Keep the existing browsing fragments, category snapshot and player session; no restart or account reset is needed.
- Add an isolated device regression for toggling both menu entries on the same nested toolbar and menu instances.

## 1.0.0-beta.8 (test candidate)

- Make channel alerts independent from following, retain an explicitly saved bell after unfollowing, and keep successful notification sources when another lookup fails.
- Add an optional active-account whisper inbox with foreground receiving, encrypted bounded local history, composing, privacy/permission guidance and an on/off setting. Twitch cannot supply old or missed inbox history.
- Add moderator channel selection and native bans, timeouts, warnings, selected-message deletion, chat modes, announcements, AutoMod holds/settings/terms, unban requests and Shield controls. Confirm mutations and reject stale account sessions.
- Link the full Twitch Mod View and poll manager for polls, predictions and remaining tools, with a clear separate-browser-account notice.
- Add live clip creation for any permitted channel, with one-request protection, rotation retention and a validated Twitch editor link.
- Expand request/account/error regressions, independent-alert coverage and isolated account-free device fixtures. See [channel tools and candidate checks](docs/CHANNEL-TOOLS.md).

## 1.0.0-beta.7

- Incorporate Xtra 2.60.9's Helix `/timeout` fix: `30s`, `5m`, `2h` and `1d` are sent as numeric seconds. Plain seconds still work and an omitted duration still means ten minutes.
- Reject invalid, overflowing or out-of-range Helix timeout durations locally, keeping them separate from explicit permanent bans. Preserve the GraphQL route and account handling.
- Bring in LizenzFass78851's German translation improvements while retaining HexStream branding, resource cleanup and quantity-aware strings.
- Update the upstream dependencies and Gradle wrapper; add timeout parsing and moderation payload regressions alongside the existing pagination, filtering, proxy, low-delay and overlay suites.
- Retain transparent/docked chat, left/right placement, inline input, global rules/proxies, neutral defaults and all project credits.

## 1.0.0-beta.6

- Replace the floating chat window with a simple transparent fullscreen chat overlay: a full-height left/right chat column over the unchanged full-size game.
- Remove the floating header, move/resize controls and window positioning. Chat is hidden/shown with the existing player controls.
- Default background opacity to zero while retaining existing chosen opacity values; contrast and optional backdrop remain under Settings → Chat.
- Add a shared left/right preference for both transparent and original docked fullscreen chat.
- Keep the existing live-chat input, replies and emotes available inside the overlay, above keyboard/control insets. Show a sign-in hint when sending is unavailable.
- Preserve the existing chat session, portrait/minimize/PiP suppression, gestures, message authorization and ThystTV/Xtra/Unwanted Twitch credits.

## 1.0.0-beta.5

- Add floating fullscreen chat, adapting ThystTV's movable/resizable overlay and same-view reparenting approach, with credit to tzii (Simone Perini) and its contributors in About and the source documentation.
- Toggle floating chat from the landscape player menu or Settings → Chat. Move and resize with the handles, choose background opacity, and toggle high-contrast text without reconnecting chat.
- Keep the same chat fragment, message history and browsing/playback session. Hide the overlay in portrait, mini-player, PiP and audio/chat-only playback; dock it when opening input or replying.
- Store bounded normalized position/size globally so the window stays within the video after a viewport change. Include accessible move/reset and tap-to-resize actions.
- Retain existing brightness, volume and minimize gestures, show/hide chat controls, pagination, full-tag rules, accounts, global proxies and low-delay tuning.

## 1.0.0-beta.4

- Add a description and practical example beneath all 224 preference rows, with current picker/input values retained and explanations included in settings search.
- Group the main settings screen and distinguish playback URL presets from HTTP host/port proxies, low-delay overrides from advanced buffering, and the separate background/PiP controls.
- Add visible examples to filter switches, lists and import/export actions, plus guidance inside proxy and video-swap editors. Retain the detailed filter explanations.
- Keep filter scroll position and expanded details when changing a switch. Distinguish same-named actions in different settings-search sections.
- Let settings explanations wrap fully instead of cutting them off at a fixed line limit. Keep token, header and account-detail values out of summaries and search.
- Document the general backup's limits: saved-account vault and separate global filter/proxy storage require their own handling.
- Preserve all existing preference keys, defaults, choices, playback behavior, global rules and proxy lists.

## 1.0.0-beta.3

- Incorporate crackededed’s Xtra 2.60.8 fixes: updated channel-points context query, player status-bar attachment guards, and unlisted video-quality recovery.
- Use the updated points request for both HexStream’s rewards panel and the inherited bonus-points context, retaining active-account handling and redemption submission guards.
- Keep recoverable quality variants, normalize missing metadata, and prefer resolution, then frame rate, then bitrate when recovering an unlisted video; audio remains last.
- Add regression coverage for the outgoing points request, response compatibility and video-quality behavior, plus an isolated-device status-bar regression fixture.
- Keep category pagination, tag filtering, global settings, proxy presets and low-delay tuning unchanged.

## 1.0.0-beta.2

- Credit Unwanted Twitch by kwaschny and its contributors in the app’s About screen for the filtering workflow and compatible list format, alongside the existing Xtra attribution.
- Make both credits prominent on the repository page and expand the dedicated credits page.
- Link the Android app and HexStream Filter browser extension, including explicit list-transfer and compatibility instructions.
- Credits/documentation update; player, filtering and pagination behavior are unchanged.

## 1.0.0-beta.1

First public HexStream beta, based on Xtra 2.60.7.

### Browsing and filters

- Append-only category snapshots with broadcaster/login/stream alias deduplication.
- Repeated-cursor and no-progress detection, bounded recovery, and internal aggregation of thin or duplicate-heavy pages.
- Separate 100-item upstream metadata batches and 30-row Paging pages; lazy thumbnails.
- Preserve visible ordering and captured viewer counts until refresh; reset on category, sort, selected-tag or language changes.
- Retain API fallback order without weakening sort/tag/language semantics.
- Native global channel/category/title/tag filters using the complete tag list supplied by each supported response.
- Exact/contains/regex rules, field/creator/word exceptions, active-account follow exemptions, optional language and emoji controls.
- Recent blocking explanations, card actions, and Unwanted Twitch/full-app JSON import/export.
- Empty editable default filter lists and opt-in language/emoji restrictions.
- Atomic compiled-filter cache and targeted list updates.

### Playback and accounts

- Six global ordered Luminous/PerfProd playback presets, with custom editing and player fallback retained.
- Saved-account switching with encrypted credentials and notifications only for the active account.
- Default low-delay live ExoPlayer tuning and compatible Media3 LoadControl forwarding.
- Optional brightness/volume swipe controls alongside existing minimize/miniplayer gestures.
- Bonus chest claims and channel reward redemption with active-account checks, confirmation and duplicate-submit protection.
- Lifecycle, player recovery, link validation, updater and accessibility improvements.

### Identity and distribution

- HexStream name and custom loop icon, including adaptive/monochrome variants.
- In-app About with Xtra credits and source links.
- HexStream-specific release feed, including beta releases.
- Locally built optimized APK, public corresponding source and documentation under AGPL v3.

See [verification](docs/VERIFICATION.md) for checks and limits. This is a beta; Twitch service/API changes and device-specific behavior remain possible.
