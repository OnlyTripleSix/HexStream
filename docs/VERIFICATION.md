# Verification and known limits

## Channel-specific moderation: 1.0.0-beta.8.2 test candidate

All **159 JVM tests /24 suites** and **23 account-free phone tests** passed. The new role regressions cover owner identity, exact broadcaster IDs, later/empty membership pages, repeated/bounded cursors, cancellation, absent and revoked roles, roles in another channel, account changes during verification, and a fresh check before each native write. Existing clip and whisper tests verify that these viewer features need no moderator lookup. The device fixture confirms an unverified channel exposes neither moderation actions nor an editable channel selector. The prior settings-menu, account, filters, proxy and overlay checks remain included.

No real moderation action, whisper or clip was submitted. Positive live moderator access still needs a permitted account with the new lookup scope. Whispers remains a limited foreground/local-history preview, without older inbox history, offline backfill or background notifications. The previous beta.8.1 menu fix is retained.

## Menu refresh: 1.0.0-beta.8.1 test candidate

All **146 JVM tests /23 suites** and **22 account-free phone tests** passed; lint reports zero errors. The new device regression changes both visibility preferences on the same nested toolbar and verifies that its view and menu instances remain in place. Returning to the app refreshes these entries through `MainActivity.onResume`, retaining the existing browsing/player fragments. No live account action was submitted.

## Channel tools: 1.0.0-beta.8 test candidate

The local candidate's complete **146 JVM tests across 23 suites passed**, with zero failures/errors/skips. Its 27 new tests cover native request payloads, moderator/sender identity, stale account rejection before and after a request, invalid timeout/chat/message values, individual chat-mode patches, selected-message versus whole-chat deletion, permitted-channel clip creation, editor/reconnect host validation, whisper/AutoMod subscriptions, both AutoMod message shapes, unban query encoding, private header/response error handling, non-replayable write bodies and an actual loopback HTTP 503/Retry-After regression, and partial notification-source failure/cancellation/deduplication. The previous pagination, full-tag, proxy, low-delay, rewards and overlay suites remain included.

Android lint reported **zero errors and 23 reviewed warnings**. Remaining warnings concern existing dependency/build-plugin availability, resource/style suggestions and explicit preference commits; no lint check was disabled for the new tools. The optimized signed release APK and isolated QA/test APKs built locally. Version, existing certificate, neutral defaults, exact source stamp and privacy checks are recorded alongside the packaged local candidate.

**All 21 account-free device tests passed** on a physical Samsung Galaxy S21 Ultra in the isolated QA package, including four new checks for disabled-inbox guidance, moderator channel selection, explicit clip creation entry and encrypted/bounded/per-account whisper history. The settings fixtures cover **230 preference rows**; the existing account, database, filter, proxy and transparent-chat fixtures also passed. The signed-in follow-list integration fixture remains excluded because it needs a dedicated QA account. No production app data was cleared, and no real whisper, clip, follow, ban or other moderation action was submitted as a test.

This is a **local test candidate on an isolated branch**, not a published replacement for beta.7. Its native tools still need intentional checks with permitted live accounts; the account-free fixtures do not certify live Twitch operations. Polls, predictions and remaining moderator tools use explicitly separate Twitch browser panels; whispers cannot retrieve old/missed conversations, and the native AutoMod queue receives new holds only while listening. [Setup, limits and manual checks](CHANNEL-TOOLS.md).

## Upstream fixes: 1.0.0-beta.7

All **119 JVM tests across 21 suites passed**, with zero failures/errors/skips. Eight new tests cover Helix timeout argument conversion, plain seconds, missing-duration defaults, suffix case, malformed/out-of-range/overflow inputs, numeric JSON duration, preserved target/reason, and explicit bans without a duration. Existing category snapshot/deduplication, filter, account-action, proxy, low-delay, quality, rewards-request and chat-overlay suites passed with the updated dependencies.

Android lint reported **zero errors and 20 reviewed warnings**: dependency update notices, the existing orientation lock, layout suggestions, compatibility resources/hooks and preference-edit suggestions. The optimized signed APK and both isolated QA/test APKs built locally with JDK 21, SDK 37 and Gradle 9.8.2. The release package/version, existing signing certificate, neutral defaults, compiled credits and exact source revision are checked during packaging; source/APK privacy scans and checksum verification accompany publication.

German resource checks preserved all **555 resource identities** in the translated strings file, format arguments and the existing quantity-aware resources. Obsolete resources were not restored. Starter filter and player/overlay sources remain unchanged. The new invalid-duration explanation currently uses English, consistent with the other added help text.

**No ADB device was connected for beta.7.** The device fixtures compiled but were not run; the beta.6 physical-device results below are historical. No personal app was installed or cleared, and no live moderation command, chat message, reward transaction or notification test was submitted. Timeout request coverage checks the shared payload builder used by all three transports, not a live Twitch moderation action. Playback/keyboard/gesture behavior with the updated dependencies still needs a device smoke check.

## Transparent chat: 1.0.0-beta.6

All **111 JVM tests across 19 suites passed**, with zero failures/errors/skips. The geometry regressions now cover the full-height left/right chat column, viewport/density changes, invalid or unmeasured bounds and mode eligibility. Android lint reported **zero errors and 23 reviewed warnings**. The optimized APK built locally with the existing signing certificate.

**All 17 account-free device tests passed** in the isolated QA package on a physical Samsung Galaxy S21 Ultra. Five overlay checks cover same-view/scroll preservation, a transparent full-height presentation with no toolbar or reduced video host, opacity/contrast with dock restoration, stored side selection, touch delivery to player controls beneath chat, and the real chat-input layout with draft/focus, keyboard inset and signed-out hint. The control-touch regression also preserves the original player-layer order: visible controls receive touches through the overlay's initial-touch pass-through, keeping the existing gesture handler in use. The four settings tests still cover all 228 preference rows. QA/test packages were removed afterward; the signed-in follow-list integration test remains excluded.

The signed candidate passed an in-place upgrade with its original first-install time preserved and a live video/chat smoke check. Left/right overlay placement, the left-side docked layout, real keyboard focus/spacing, right-side chat-button hide/show, and minimize/restore were exercised. No message was submitted. The layout retains the same fragment/session and the existing portrait/minimize/PiP eligibility paths. The live composer stays in the overlay, with keyboard/control insets, account gating, replies and emotes. PiP/audio-only transitions, reward spending, real notification delivery and backup round trips were not repeated against a live account for this update. One phone and its existing font/theme configuration do not cover every device or display setup.

The default backdrop is now fully transparent. Existing chosen opacity values are retained; the old beta.5 window geometry is ignored. ThystTV, Xtra, Unwanted Twitch and dependency attribution remain intact. Pagination, filters, account handling, global proxies and live-delay policies are unchanged.

## Floating chat: 1.0.0-beta.5

All **110 JVM tests across 19 suites passed**, with zero failures/errors/skips. Nine new geometry tests cover bounds, normalization, resizing, invalid stored values and mode eligibility. Android lint reported **zero errors and the same 23 reviewed warnings** as beta.4. The optimized APK built locally with the existing signing certificate.

**All 15 account-free device tests passed** on a physical Samsung Galaxy S21 Ultra in the isolated QA package. Three new floating-chat checks exercise repeated moves between hosts while retaining the same view and scroll position; bounded dragging and cancellation; resizing, contrast and hiding. The four settings tests now include all 227 preference rows. Fixtures wait for actual layout, keep their test activity awake and use only synthetic chat. QA/test packages were removed afterward; the signed-in follow-list integration test remains excluded.

The signed candidate passed an in-place upgrade with its original first-install time preserved. A live-stream smoke check confirmed video/chat rendering, enabling floating mode from the landscape player menu, dragging, resizing, contrast changes, accessible reset, hide/show, minimize/restore and return to docked portrait chat. These checks do not measure socket traffic or certify every device, font scale or theme. PiP/audio-only suppression has deterministic eligibility coverage; those transitions were not repeated against the live stream. Reward spending, real notification delivery and backup/restore round trips were not repeated.

The overlay adapts [ThystTV's work](upstream/THYST-CHAT.md), with attribution in About and the source. It uses the existing chat view and connection; no second chat fragment, system overlay permission or new network route is introduced. Existing pagination, filtering, accounts, proxy and low-delay implementations are unchanged.

## Settings explanations: 1.0.0-beta.4

All **101 JVM tests across 18 suites passed**, with zero failures/errors/skips. Android lint reported **zero errors and the same 23 reviewed warnings** as beta.3. The optimized signed release APK built locally. Preference-resource comparison confirmed all **224 existing keys, defaults, choice arrays and other behavior attributes** were preserved; only help text, labels and main-screen grouping changed in those resources.

**Four new device tests passed** on a physical Samsung Galaxy S21 Ultra in the isolated QA package. They check that every preference row inflates with an example; selected-value changes retain help without writing preferences; credential/header/account values stay out of summaries; and all 13 settings screens render with unrestricted summary line counts. The render fixture waits for navigation transitions before checking the views. QA/test packages were removed afterward. The personal app was neither updated nor cleared.

Root and buffer-screen captures were inspected. This run used the phone's existing display/font configuration; it did not independently test every font scale or device size. The full-line layout avoids the inherited fixed summary-line cap. New help is currently English. Live playback, actual reward spending, real notification delivery and backup/restore round trips were not repeated for this settings update.

The filter screen's immediate-save flow retains scroll position and open detail sections on a switch change. Its rule engine, preset storage, account switching, pagination and low-delay implementations are unchanged. See the [settings guide](SETTINGS.md) for the distinctions between similar controls and the limits of the inherited general backup format.

## Upstream fixes: 1.0.0-beta.3

Beta.3 incorporates the three Xtra 2.60.8 fixes listed in the [synchronization record](upstream/SYNC.md). **101 JVM tests across 18 suites passed**, including intercepted outgoing points requests, compatibility with both points response consumers, optional video-quality metadata and recovery ordering. The existing pagination, filter, proxy, account-action and low-delay suites passed too.

Android lint reported **zero errors and 23 reviewed warnings**, the same warning categories as beta.2. The optimized signed APK built locally; its package/version, existing signing certificate, neutral starter lists, compiled attribution and updated points-query hash were verified. The status-bar device regression fixture compiled and passed on a physical Samsung Galaxy S21 Ultra in the isolated QA package.

An anonymous read-only request to Twitch using the updated points query returned HTTP 200 with data and zero GraphQL errors. No account credentials were used and no bonus claim or reward redemption was submitted. This checks query availability, not authenticated point-spending behavior.

The beta.3 candidate was installed in place over beta.1 and launched successfully, with its original first-install time preserved. The unattached-player status-bar regression passed as a separate device test. Live playback, quality selection, minimize/back browsing and real point-spending were not repeated for beta.3; the broader device results below remain historical beta.1 results.

## Credits update: 1.0.0-beta.2

The credits update passed the 90 JVM tests again. Android lint reported zero errors and 23 reviewed warnings covering dependency updates, compatibility hooks and UI suggestions; the optimized APK was built locally. Its package, version, existing signing certificate, empty starter lists and compiled Xtra/Unwanted Twitch attribution were verified. Only the app’s About text and version metadata changed; playback, pagination and filter behavior are unchanged.

The physical-device results below refer to beta.1. Device tests were not repeated for this credits-only update.

## Automated checks

The first public beta passes **90 JVM tests across 16 suites**, checked locally with JDK 21, the included Gradle wrapper and Android SDK 37. The JVM suites cover:

- Category overlap, thin/repeated/empty pages, cursor loops, blank final cursors, fallback overlap, missing broadcaster IDs, large identity sets, cancellation and retry.
- Full supplied-tag matching, rule/allow-list scoping, Unicode matching, emoji sequences, validation and neutral defaults.
- Concurrent filter-cache access and engine/rule pairing.
- Proxy preset ordering and seed behavior.
- Live buffer/offset policies and Media3 LoadControl contract forwarding.
- Swipe selection, Twitch URL validation, channel-points parsing/submission gates and release-feed parsing.

Android lint and the optimized R8 release build are part of the local checks. The lint report has **zero errors and 22 reviewed warnings**: dependency update suggestions, existing compatibility APIs/hooks and UI/layout suggestions. They are not all eliminated, and the project does not claim a warning-free build. Dependency migrations are kept separate from the first beta’s behavior changes.

## Device checks

The public source passed seven account-free QA checks on a physical Samsung Galaxy S21 Ultra through AndroidJUnitRunner. The follow-list integration test needs a signed-in test account and was excluded. The Gradle device task reported an aggregate failure despite its seven-test XML reporting zero failures; direct runner output independently confirmed `OK (7 tests)`. Unit, lint and release-build tasks passed normally.

The stable source preceding the public packaging also underwent a roughly two-minute large-category scroll that sampled 49 distinct displayed names without a sampled reappearance; upstream overlapping rows were dropped. Background/resume, refresh and selected-sort/language/tag resets were exercised.

These samples complement deterministic pagination tests. They do not prove that every possible live directory response or device is covered. The optimized public APK passed an in-place upgrade, cold launch, About/attribution and live playback smoke check. Its first-install identity and signing certificate were preserved.

## Short manual checklist

1. **Browse:** slowly scroll a large category. Creators should not repeat and visible rows should not reshuffle.
2. **Refresh:** refresh manually; a fresh snapshot and viewer counts should load.
3. **Query changes:** change sort, selected tag and language; each should start a new list while preserving the selected semantics.
4. **Rules:** add a temporary rule for a supplied tag, including one outside the visible chips. Inspect “Why blocked?” and remove the rule afterward.
5. **Accounts/proxies:** switch accounts; global rules and edited proxy order should stay. Select a preset/custom endpoint and verify playback/fallback behavior.
6. **Player:** play a live stream, test LIVE, volume/brightness swipes, minimize/restore and background/resume.
7. **Points:** load the balance/rewards. Claim or spend only when you intend to perform the actual Twitch action.

## Limits

- Twitch directory churn prevents a complete server-side snapshot; bounded recovery may stop early on an unstable or heavily filtered query.
- Tags and other filter metadata are only as complete as the chosen API response.
- Proxy uptime, a fixed live-delay guarantee, long battery endurance and all device/OEM combinations are not certified.
- Real point-spending transactions and actual push/notification delivery are not part of unattended tests.
- Internal Twitch APIs can change. A failed or ambiguous mutation is not automatically retried.
- No software release can promise zero bugs. Report reproducible issues with redacted diagnostics and the exact version.
