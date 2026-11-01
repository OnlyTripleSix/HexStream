# Xtra synchronization

HexStream started from Xtra 2.60.7, commit [`a3cbb0325f3330573a6738f7d36a2c5b785f4d1c`](https://github.com/crackededed/Xtra/commit/a3cbb0325f3330573a6738f7d36a2c5b785f4d1c). Its initial public commit is a source snapshot rather than a shared-history Git fork. Compare against the recorded upstream checkpoint when integrating later changes.

## Current integrated checkpoint

HexStream **1.0.0-beta.9** incorporates application changes through **Xtra 2.60.10**, commit [`0ab697de04799365f590f91e2dfbefc092c3ad5a`](https://github.com/crackededed/Xtra/commit/0ab697de04799365f590f91e2dfbefc092c3ad5a). HexStream keeps its own version, package and signing identity, explained settings and [credited ThystTV chat adaptation](THYST-CHAT.md), with the frameless transparent overlay, side selection and inline composer introduced in beta.6.

The tested beta.8 channel tools are retained in this release. Preserve their independent notification list, optional foreground whisper lifecycle, owned web-history paging, encrypted per-account cache, exact-channel moderation checks, non-replayable writes and separate-browser-account notices during future merges. See [setup and limits](../CHANNEL-TOOLS.md).

## Xtra 2.60.10 integration

Both original commits below were authored by **crackededed** on November 1, 2026.

| Original upstream commit | Change | HexStream adaptation |
| --- | --- | --- |
| [`47c8d6db00bbc065719718fe69be710c406cac13`](https://github.com/crackededed/Xtra/commit/47c8d6db00bbc065719718fe69be710c406cac13) | Fix download-progress updates causing errors | Service callbacks post progress to ViewModel-owned state. The fragment applies it only in its STARTED lifecycle. Copy mutable service objects, coalesce per download, and acknowledge only the rendered snapshots so simultaneous downloads and newer callbacks are retained. |
| [`0ab697de04799365f590f91e2dfbefc092c3ad5a`](https://github.com/crackededed/Xtra/commit/0ab697de04799365f590f91e2dfbefc092c3ad5a) | Fix stream-download crashes and update build plugins | Replace the accumulating segment URL with the latest completed segment in all six transport/write paths through a shared operation. Apply upstream database 40→41 bookmark reset while retaining saved files, row identity, bytes and progress. Update AGP 9.5.0/KSP 2.3.13; preserve HexStream version/signature and Gradle 9.8.2. |

The download regressions cover stopped-screen replay, multiple active downloads, service-object reuse, newer callbacks during rendering, byte totals and a large segment sequence with one bounded resume bookmark. The exact production repair SQL is also exercised against synthetic SQLite records. Neither check submits a live Twitch download or opens a personal database. Existing pagination/API fallback ordering, tags/language/sort rules, accounts, playback, proxies, rewards and gestures remain unchanged.

## Xtra 2.60.9 integration

| Original upstream commit and author | Change | HexStream adaptation |
| --- | --- | --- |
| [`bce2c374624e1dd9ab192763a3fc09049db8c646`](https://github.com/crackededed/Xtra/commit/bce2c374624e1dd9ab192763a3fc09049db8c646), **LizenzFass78851**, October 26, 2026 | German translation improvements ([#1077](https://github.com/crackededed/Xtra/pull/1077)) | Update still-used strings and follower plurals. Keep HexStream's resource cleanup and quantity-aware points/raid resources; retain HexStream branding in the return-to-app instruction. Xtra's F-Droid store descriptions are not used by this distribution. |
| [`0444de816e28de1654979a5a2a663b3fc51cb6a6`](https://github.com/crackededed/Xtra/commit/0444de816e28de1654979a5a2a663b3fc51cb6a6), **crackededed**, October 26, 2026 | Helix timeout duration conversion, dependency and wrapper updates | Convert `s`/`m`/`h`/`d` to numeric seconds. Preserve plain seconds and the missing-argument ten-minute default. Reject malformed, overflowing or out-of-range durations instead of submitting a different timeout or accidentally omitting the duration. Keep explicit bans separate. Retain API choice/order and account handling. |

The accepted range follows [Twitch's Ban User reference](https://dev.twitch.tv/docs/api/reference/#ban-user): 1–1,209,600 seconds. The GraphQL command path is unchanged. Regression tests check numeric JSON payloads, target/reason preservation, explicit bans, both argument styles, case, bounds and overflow; they do not send a live moderation action.

The upstream dependency changes are Conscrypt **2.8.0**, Kotlin **2.4.21**, OkHttp **5.5.1**, Paging **3.5.3**, serialization **1.12.0**, WebKit **1.18.0**, and Gradle wrapper **9.8.2**. Existing pagination, full supplied-tag rules, shared proxies, player/chat layout, gestures and rewards remain in place. No change to API fallback ordering or filter semantics is required.

## Previously integrated Xtra 2.60.8 changes

All three original changes below were authored by **crackededed** on October 14, 2026.

| Original upstream commit | Fix | HexStream adaptation |
| --- | --- | --- |
| [`0497f3d2ca9085abd72102b609d3c6ef838bfee6`](https://github.com/crackededed/Xtra/commit/0497f3d2ca9085abd72102b609d3c6ef838bfee6) | Unlisted video qualities | Preserve upstream reason exclusions, optional metadata and resolution/frame-rate/bitrate ordering. Small shared functions allow deterministic JVM coverage. |
| [`b24b45c7b85b01d85d284eb51dbb964a8d3eea5d`](https://github.com/crackededed/Xtra/commit/b24b45c7b85b01d85d284eb51dbb964a8d3eea5d) | Player status-bar crash | Apply upstream `isAdded` guards to showing and hiding system bars. Existing minimize/swipe behavior remains in place. |
| [`96772030c7712581ad150c9b8b619c77a3527e50`](https://github.com/crackededed/Xtra/commit/96772030c7712581ad150c9b8b619c77a3527e50) | Channel-points context query | Update the persisted-query hash and `includeGoalTypes` in HexStream’s shared viewer-points request. Both the custom rewards panel and inherited bonus context use it. Redemption/claim mutations are unchanged. |

## Checks for this integration

- Run the JVM suites, including the intercepted HTTP request/response checks and unlisted-quality regressions.
- Run Android lint and build the optimized signed APK locally.
- Compile the isolated QA instrumentation fixtures; run the status-bar fixture on a connected test device when available.
- On a device, check live playback, minimize/restore and back navigation, quality selection/recovery, and loading points/rewards. Spend or claim points only intentionally.
- Confirm the existing pagination, filter, proxy and low-delay suites still pass. No changes to those implementations are required by this synchronization.

The machine-readable checkpoint is [SYNC.json](SYNC.json); update it alongside this record after integrating upstream changes.

Record actual results in [verification](../VERIFICATION.md). Compiling a fixture is not the same as running it, and a successful mocked response does not certify Twitch’s live API behavior.

## Future updates

Fetch `crackededed/Xtra` and review commits after the checkpoint above. Adapt changes in areas HexStream extends, preserve upstream attribution and licenses, run the relevant regression suites and build locally. Update this checkpoint only after incorporating all intended changes through the new commit. Do not replace HexStream’s customized source wholesale with an upstream tree.

Player/chat updates must also preserve the chat overlay's single existing fragment/connection, full-video layout, frameless transparent default, selectable side, minimize/PiP/portrait suppression and inline composer/keyboard handling, along with the existing swipe controls. Run both the geometry and isolated-view regressions when modifying those paths.

A completed update includes the tested source on the existing repository’s main branch and a downloadable GitHub release with the signed APK, matching source ZIP and SHA-256 checksums. Builds run locally. Preserve the package/signing identity, empty defaults, credits and published release tags. A failed check leaves the previous downloadable release in place.
