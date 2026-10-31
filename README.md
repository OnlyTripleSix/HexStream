<p align="center">
  <img src="docs/assets/hexstream-banner.svg" alt="HexStream by TripleSix — Your stream. Your rules." width="900">
</p>

<p align="center">
  <a href="https://github.com/OnlyTripleSix/HexStream/releases"><img alt="Download beta" src="https://img.shields.io/badge/Download-Android_beta-FFF5E6?style=for-the-badge&labelColor=171B1B"></a>
  <img alt="Android 7 or newer" src="https://img.shields.io/badge/Android-7.0%2B-83D1B8?style=for-the-badge&labelColor=171B1B">
  <a href="LICENSE"><img alt="AGPL v3" src="https://img.shields.io/badge/License-AGPL_v3-FFF5E6?style=for-the-badge&labelColor=171B1B"></a>
  <a href="https://github.com/crackededed/Xtra"><img alt="Built on Xtra" src="https://img.shields.io/badge/Built_on-Xtra-83D1B8?style=for-the-badge&labelColor=171B1B"></a>
  <a href="https://github.com/OnlyTripleSix/HexStream-Filter"><img alt="Browser extension" src="https://img.shields.io/badge/Also_on-Firefox_%26_Chrome-FFF5E6?style=for-the-badge&labelColor=171B1B"></a>
</p>

**Twitch on your phone, without the shit you didn’t come for.**

HexStream is a custom Android Twitch client by TripleSix, built on [Xtra](https://github.com/crackededed/Xtra). Browse without the same streamer coming back for an encore, filter the tags you don’t care about, keep your accounts handy, and get back to watching the damn game.

Xtra supplies the foundation: playback, chat, browsing and much more. Credit belongs to **[Andrey Asadchy](https://github.com/AndreyAsadchy/Xtra), [crackededed](https://github.com/crackededed/Xtra), and Xtra’s contributors**. HexStream extends that work under the **GNU Affero General Public License v3**. See [credits and provenance](CREDITS.md).

The filtering workflow and compatible list format were inspired by **[Unwanted Twitch by kwaschny](https://github.com/kwaschny/unwanted-twitch)**. HexStream implements those ideas in its own native Android filtering engine. Both projects are credited in the app’s **About** screen and [credits page](CREDITS.md).

## Get HexStream

Download the APK from [Releases](https://github.com/OnlyTripleSix/HexStream/releases). The current release is **1.0.0-beta.7**, built locally with Gradle and the Android SDK. Android 7.0 or newer is required. No GitHub Actions build is used.

Beta.7 adds Xtra 2.60.9's Helix timeout fix and German translation improvements. Fullscreen chat can sit transparently over the full-size game or beside it, on either side, with typing/replies/emotes. That work adapts [ThystTV by tzii and its contributors](https://github.com/tzii/ThystTV). The explained settings, stable category browsing and full supplied-tag rules stay intact. [Settings guide →](docs/SETTINGS.md) · [What changed →](CHANGELOG.md)

Fresh installations start with **empty filter lists**. Language restrictions and emoji blocking are off; you choose the rules. Upgrading preserves existing saved rules, accounts and proxy settings. See [installation and builds](docs/BUILDING.md) for signing and package details.

> **Want filtering in your browser too?** [HexStream Filter for Firefox and Chrome →](https://github.com/OnlyTripleSix/HexStream-Filter)<br>
> Hide users, categories, titles and every supplied tag—including tags the card leaves out. Import compatible lists, inspect why something disappeared and pick character filters from an easy Languages page. Firefox downloads are Mozilla-signed; Chrome uses an unpacked ZIP. [Install the extension →](https://github.com/OnlyTripleSix/HexStream-Filter/blob/main/docs/INSTALLING.md)

The app and extension are separate installations with explicit compatible-list export/import. There is no automatic phone/browser sync. Android's language identification and emoji controls differ from the extension's character presets, and advanced regex dialects can differ. [Browser filtering and migration details →](https://github.com/OnlyTripleSix/HexStream-Filter/blob/main/docs/FILTERING.md#using-it-with-hexstream-for-android)

> **Beta.8.2 test candidate:** independent channel alerts, a limited foreground whisper preview, automatically detected channel moderation controls and live clip creation. These additions still need live-account checks before release. [Setup, coverage and limits →](docs/CHANNEL-TOOLS.md)

## What HexStream adds

| Feature | What you get |
| --- | --- |
| **Stable category browsing** | An append-only browsing session, broadcaster deduplication, cursor-loop protection and internal recovery from thin pages. Visible rows keep their order and viewer counts until refresh. |
| **Full supplied-tag filtering** | Filter every tag returned for a stream, including tags outside a card’s visible chips. Channel, category and title rules are separate. |
| **Global filtering controls** | Exact, contains and regex rules; narrow allow lists; optional language and emoji checks; import/export compatible with Unwanted Twitch. |
| **“Why blocked?”** | Inspect recent hidden items and the precise rules or language/emoji evidence that hid them. Edit the rule or add a narrow exception. |
| **Multiple saved accounts** | One active account at a time, encrypted saved credentials, and notifications scoped to that active account. Global filters and proxies survive switches. |
| **Six playback proxy presets** | Ordered Luminous and PerfProd endpoints, editable custom proxies, and the existing player’s fallback behavior. Shared by all accounts. |
| **Lower live delay** | ExoPlayer starts closer to the live edge by default, with bounded speed adjustment and live-specific buffering. Network and source conditions still matter. |
| **Swipe controls** | Left-side brightness and right-side volume, independently configurable, alongside the existing minimize/miniplayer gestures. |
| **Explained settings** | Descriptions and examples under every preference, current values kept visible, and searchable explanations of overlapping controls. |
| **Transparent fullscreen chat** | Chat sits directly over the full-size game, without a floating window or toolbar. Choose left/right placement, type/reply at the bottom, and adjust background opacity/contrast; same chat session. Adapted from ThystTV, with attribution. |
| **Channel points** | Claim an available bonus chest and redeem available channel rewards, with balance, input and confirmation handling. |
| **HexStream identity** | Custom launcher/adaptive/monochrome icon, About credits, and a HexStream release feed. |

The existing Xtra player, chat/emotes, VODs, clips, downloads, picture-in-picture, themes and other settings remain part of the foundation. A desktop edition is not included.

## Twitch’s pagination needs a babysitter

Scroll down. Same streamer. Scroll farther. Same streamer again. Apparently “next page” needed a damn explanation.

Twitch’s live directory changes while you scroll. [Its own API documentation warns that changing viewer counts can produce duplicate or missing streams across pages.](https://dev.twitch.tv/docs/api/reference#get-streams) Simply appending those responses can repeat creators; a client also needs to defend against thin pages and cursors that stop advancing.

HexStream keeps one client-side snapshot for the current category query:

1. Fetch metadata in batches of **100**, independently of the **30-row** Android Paging page size.
2. Apply the current content rules to the full supplied metadata.
3. Deduplicate by broadcaster identity, with channel login and stream ID as additional aliases/fallbacks.
4. Buffer genuinely new rows. Consume duplicate-heavy or empty pages internally while looking for enough new rows.
5. Append accepted rows without moving or rewriting rows already shown.

Recovery is bounded: at most **6 upstream requests per load**, with a **3-page no-progress limit** per API path. Repeated cursors trigger a compatible fallback rather than an endless loop. The normal GraphQL → persisted GraphQL → eligible Helix ordering is retained; Helix is excluded when it cannot preserve the selected tags, language or sort.

Manual refresh, category, sort, tag and language changes create a new snapshot. Ordinary scrolling and back navigation keep the session while its ViewModel survives. Thumbnails remain lazy. [Read the design, limits, counters and test cases →](docs/PAGINATION.md)

## The Twitch tags and filtering system

This is a **native Android implementation** inspired by the workflow and JSON format of [Unwanted Twitch](https://github.com/kwaschny/unwanted-twitch), not a bundled browser extension.

Ever opened Twitch for a game and landed in a tag war between `MyTeamIsPerfect` and `EveryoneElseIsWrong`? **Cool. I don’t give a fuck. I’m here for the game.**

Pick whichever camp you’re tired of seeing. Pick both. Pick neither. HexStream lets you filter the tags and get on with watching. These are fictional examples; there is no preloaded list telling you which side to like.

The filter engine checks the **entire tag list supplied by the selected Twitch API**, not just the first few visible tags. It also checks channel identity, title and category where that metadata is available. It does not invent missing tags or maintain a complete catalog of every Twitch tag.

Rules are global across saved accounts. Long-press a supported stream card to block or allow a channel, title, tag or emoji. Open the content-filter settings for full lists, optional language controls, imports/exports and recent blocking explanations. Creator allow rules and the optional followed-channel exception take precedence; field and word exceptions stay narrow.

**No preselected channels, tags, categories or titles are blocked.** Optional language and emoji controls are user choices. [Read matching rules, precedence, metadata coverage and import/export limits →](docs/FILTERING.md)

## Playback and accounts

The preset order is **EU2 Luminous → EU Luminous → PerfProd North America → PerfProd Europe → PerfProd Europe 2 → PerfProd Asia**. The list is seeded once globally, so switching accounts does not restore deleted presets or replace custom edits. Proxy availability and delay depend on independent operators.

The player preserves the first working custom endpoint for the playback session and can move through configured endpoints on supported failures. This is not a background proxy benchmark. Low-delay mode is enabled by default for live ExoPlayer streams; it is a tuning target, not a promise of a fixed end-to-end delay.

Saved accounts share browsing filters and proxy configuration. Account credentials and account data are separate; the notification worker checks which account is active before posting. Rewards and bonus claims use the active account, with confirmation and duplicate-submit protection. [Playback, presets, gestures and account behavior →](docs/PLAYBACK.md)

## Quality and development

Pagination, filtering, concurrent filter caching, proxy seeding, update parsing, reward submission gates and player compatibility have automated coverage. Local release builds, Android lint and physical-device smoke checks supplement those tests. See [verification and known limits](docs/VERIFICATION.md).

- [Build from source](docs/BUILDING.md)
- [Changelog](CHANGELOG.md)
- [Credits and upstream provenance](CREDITS.md)
- [Privacy and security](docs/PRIVACY.md)
- [Report a bug](https://github.com/OnlyTripleSix/HexStream/issues/new)

When reporting a problem, include the app version, Android version, steps and relevant settings. Remove account details, tokens, cookies and personal filter exports before attaching logs.

HexStream is independent of Twitch and is not affiliated with or endorsed by Twitch, Xtra’s maintainers, or the listed proxy operators. Twitch services and internal APIs can change.
