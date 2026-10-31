# Settings without the guessing game

Open **Settings** from the main menu. Each option has a short description and a practical example beneath it. Pickers keep their current selection visible above the explanation. Search checks these descriptions too: try “mobile data”, “screen locked” or “proxy”.

The main screen groups app/browsing controls, watching/listening, proxies, downloads/updates/data and advanced troubleshooting. Nothing is secretly reset when you open it. Menu language, chat translation and stream-language filters are separate choices.

## Similar-looking controls that do different jobs

| Controls | Difference and example |
| --- | --- |
| Background audio / locked-screen audio | One covers leaving the app with the screen awake; the other covers a locked screen outside PiP. Enable both to listen in both situations. |
| PiP / its two audio switches | PiP is the floating picture. The player settings decide whether sound continues after closing that window or locking the phone from PiP. |
| Disable background video rendering / audio-only track | The first stops drawing video. The second switches to an available audio-only stream to reduce transferred video data, which can cause a brief reload. |
| Low-delay mode / advanced live buffers | Low-delay mode supplies its own live startup/rebuffer thresholds, target offset and catch-up speeds. Turn it off before expecting those advanced values to control live timing. Reopen playback after changing timing settings. |
| Minimum/maximum buffer / startup buffer / rebuffer | Loading thresholds, the amount needed to begin playback, and the amount needed after a stall. Milliseconds are time: 2000 means two seconds. A loading target is not a promise of end-to-end live delay. |
| Playback URL presets / HTTP host-and-port proxies | URL presets include the shared Luminous/PerfProd list. HTTP entries configure your own server and selected request types; those inherited entries belong to the active account. URL proxy mode takes priority. |
| Proxy modes / video swap | Video swap operates on the direct route during detected ads. Selecting either proxy mode takes priority over video swap. |
| Player buttons / player menu | Two places to expose an action. For a cleaner picture, hide a standalone Quality button and keep Quality in the menu. |
| Directory uptime / player uptime | One is on browsing cards; the other is on the video controls. You can keep either or both. |
| Chat size modifier / individual sizes | The modifier scales chat together. Text, emote, badge and GIF sizes set the individual base sizes. |
| Recent-message limit / message limit | How many old messages to request on joining versus how many messages to retain during viewing. |
| Simultaneous downloads / download threads | Number of jobs versus chunks fetched concurrently inside a job. Lower either if downloads crowd out playback. |
| App language / translation / stream filters | Menus, how received chat is translated, and what browsing results are hidden. Changing one does not select the others. |

## Transparent fullscreen chat

While watching in landscape, open the **player menu → Fullscreen chat overlay**. The same switch is at **Settings → Chat**. Docked chat remains the default.

With overlay enabled, chat appears directly over the selected side of the full-size game. With it disabled, the original docked layout puts chat beside the picture. **Fullscreen chat side** chooses left or right for either layout. There is no floating window, header, border, move handle or resize toolbar. The usual player chat button hides/shows it; disable overlay mode to return to the sidebar.

**Background opacity** defaults to `0`: only the text/emotes cover the picture. Raise it if busy gameplay makes chat hard to read; text and emotes keep their opacity. Existing opacity choices from beta.5 are retained, so choose `0` for a fully transparent background after upgrading. **High-contrast text** adds bold text and a dark shadow.

Portrait, the mini-player, PiP and audio/chat-only playback use their normal layouts. The message box stays at the bottom of overlay chat for live streams. Signed-in viewers can type/reply and use emotes there; signed-out viewers see “Sign in to chat”. The keyboard gets its own inset while video remains in overlay mode. The existing player chat-input toggle can hide/show the box. Brightness/volume and center-minimize gestures continue outside the chat column. Settings are shared across accounts; no draw-over-other-apps permission is needed.

This feature adapts [ThystTV's work](upstream/THYST-CHAT.md), credited to tzii (Simone Perini) and its contributors.

## Filters and exceptions

Open **content filters** from the main menu. All filter switches and lists have visible examples; expanded explanations retain the detailed rules and limitations. Changes save immediately. Changing a switch keeps your scroll position and any open explanation sections.

- **Allowed channels** exempts an entire creator. **Allowed usernames**, **titles** and **tags** exempt only matching fields. Other fields can still hide that stream.
- **Allowed words** protects complete words. For example, allowing `café` protects that word rather than allowing an entire title or creator.
- **English only** uses language evidence. The independent character switch can also match accents in an otherwise English title; title detection and declared-language checks have their own controls.
- Hiding visible tag chips under UI settings does **not** disable full supplied-tag filtering.
- **Why blocked?** helps identify the matching rule before you change anything. Starter lists remain empty and language/emoji restrictions remain off until chosen.

## Moderation timeout commands

With **Use API chat commands** enabled and a moderator account using the Helix route, `/timeout SomeChannel 5m` requests five minutes. You can use plain seconds (`30`) or one `s`, `m`, `h` or `d` suffix (`30s`, `5m`, `2h`, `1d`). Leaving out the duration uses ten minutes. Helix accepts 1 second through 14 days; malformed/out-of-range values show an error without submitting the timeout. `/ban` remains a separate permanent-ban command. These commands perform real moderation when you submit them; use them only in a channel you moderate.

The GraphQL route, permissions and API selection are unchanged by this fix. Ordinary chat messages do not need a new setting.

## Backups: know what is in the file

The inherited **Backup settings and data** action exports general preferences and the app database. It is not a complete backup of the encrypted saved-account vault or the separate global filter/proxy stores. Do not treat it as a one-step phone migration.

Use **Export full filter settings** on the filter screen to preserve its switches, lists and exceptions. The Unwanted Twitch export covers compatible blocking lists, not all app-specific options. Record custom proxy edits separately and sign in again where needed. Backups and filter exports can reveal personal preferences; choose where to store them accordingly.

Token, request-header and account-detail values are not copied into settings summaries or search results. The credential editors remain advanced account-repair tools; normal account switching belongs in the account menu.

New help text currently falls back to English when a translation is unavailable. Existing translated setting titles remain available.

## Channel tools in the beta.8 candidate

Chat settings include **Whispers** (off initially) and **Moderator tools** (shown initially). Each has inline help and an example. Whisper receiving runs only while the app is visible and only for the active account; turning it off removes its menu and connection. Moderator tools can be hidden independently. [Login permissions, browser account boundaries and instructions](CHANNEL-TOOLS.md).
