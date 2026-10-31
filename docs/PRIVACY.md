# Privacy and security

HexStream is a third-party Twitch client. Its source is public so its behavior can be inspected. It is not affiliated with Twitch.

## Local data

Saved account credentials are encrypted using an Android Keystore-backed AES-GCM vault. Account-specific databases are separate from global filters and playback proxy configuration. Android’s app sandbox protects the application’s storage; device compromise, an unlocked device or deliberately exported data can still expose information.

Filter lists and exceptions are stored locally. The recent “Why blocked?” list is bounded in-memory diagnostic state. Public downloads contain empty editable filter lists, not a curated channel/tag blocklist. Upgrades preserve the user’s existing saved settings.

Filter exports, app backups and logs can contain personal channel preferences or account-related data. Review them before sharing. Never submit tokens, cookies, passwords, recovery material or signing keys in an issue.

## Network services

The app connects to Twitch for browsing, playback, chat, account information and supported actions. Depending on enabled features, inherited Xtra integrations also contact emote services, image hosts and configured network providers. Optional title-language detection runs through ML Kit on the device; it does not transcribe stream audio.

Custom playback proxies and general network proxies are different settings. A playback endpoint receives the requested channel and playback traffic. A configured network proxy can carry other requests, including authenticated Twitch API traffic when that option is enabled. Custom URLs, query parameters, headers and proxy authentication are user-controlled. Only configure services you trust; the app cannot make an independent proxy private or guarantee what its operator records.

The HexStream updater reads public release metadata from GitHub. Automatic update checking is optional. Downloads use the app’s update flow and Android’s installer confirmation. The update feed is HexStream-specific; upstream Xtra releases are not the default update source.

## Diagnostics and issue reports

Category snapshot counters are enabled only in debug builds and contain counts, not authentication headers, cookies, tokens or playback URLs. Other inherited diagnostics and Android logs should still be reviewed before sharing.

For a useful report, provide the app version, Android/device model, reproduction steps, selected sort/tag/language settings, direct/proxy playback mode and a redacted error description. A full account backup or filter export is usually unnecessary.

For a sensitive vulnerability, avoid putting exploitable details or credentials in a public issue. Use GitHub’s private vulnerability reporting when available on the repository. Never attach a real account token as a reproduction aid.

## Channel tools candidate

Optional whisper history loads from Twitch using the active account's existing web login. The returned owner and conversation participants are verified before importing data. Its local cache is encrypted with a device-bound Android Keystore key in a separate private store, bounded to 500 messages per account and excluded from app settings/filter exports. Clearing the cache does not delete Twitch conversations. History reads do not mark messages read or archive threads. Receiving uses the active account while the app is visible; background whisper alerts are not implemented. Native moderator, whisper and clip requests have logging interceptors and automatic mutation retries disabled. Browser Mod View, polls and clip editing use the browser’s own login, with no token or cookie handoff. See [channel tools](CHANNEL-TOOLS.md) for the history and API limitations.
