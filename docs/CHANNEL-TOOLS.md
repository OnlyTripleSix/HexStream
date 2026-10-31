# Channel alerts, whispers, moderation and clips

These features are included in the **beta.8 test candidate**. The last published beta remains available while the candidate receives phone and live-account checks.

## Get connected

Native moderator actions, whispers and clip creation use the active account's Twitch API login. Existing accounts may need to grant the new permissions:

1. Open **Settings → API token settings → Get token for selected APIs**.
2. Choose **Both** (keep the existing web/chat capabilities too) or **Helix**.
3. Log out and log back into the account you intend to use. Check the username shown at the top of the tools page.
4. Grant the requested permissions. A saved account does not gain these permissions merely because the APK was updated.

Do this separately for each account you use. If Twitch refuses an action, check both the login permission and your role in that particular channel. A moderator role in one channel does not grant rights in another. Native requests capture the active account and reject results if its account or credentials change; mutations are not automatically retried.

## Alerts without following

Open the channel page and tap its bell. Following is a separate choice. The bell can save a channel in the active account's notification list without sending a follow request. An explicitly saved alert survives unfollowing; turn the bell off when you no longer want it.

Android notification permission and the app's global live-notification switch must be on. Existing notification polling uses Android's periodic background work, so this is not an instant go-live push guarantee. Notification filtering still applies, and only the active account's worker posts alerts.

A failed lookup for followed channels no longer discards successful checks for independently saved channels. When one source is unavailable, its previous notification markers are retained so recovery does not repeat notifications just because a request failed.

## Whispers

Enable **Settings → Chat → Whispers**. This shows Whispers in the browsing overflow menu and on a selected chat message. Turn it off to hide the entry points and close its receiving connection. The existing chat `/w` command remains available independently.

Use **New conversation** to enter a Twitch username, or choose Whispers on a chat message. Type a message and confirm Send. An accepted submission is not a delivery receipt; Twitch can enforce account verification, recipient privacy settings and other restrictions. There is no automatic resend after an uncertain connection failure.

One receiving connection serves the active account while HexStream has a visible screen. It closes after the app moves into the background and reconnects when you return. Inactive accounts have no whisper socket, background notification worker or inbox polling. Ordinary browsing, player and chat sessions keep their existing connections.

History is **local to this installation**: at most 500 messages per account, encrypted with a device-bound Android Keystore key in a separate private store. It is not included in filter exports or the general settings backup. The conversation list shows the latest 100 correspondents; an open conversation shows its latest 100 messages. Clear local history from Whispers; clearing one account leaves the others alone.

Twitch's public interface provides sending and incoming EventSub events, rather than an old-message history endpoint. This inbox cannot recover older Twitch conversations or messages received while HexStream was disconnected. The connection supports keepalive recovery, reconnect handoffs and bounded duplicate-event tracking. Connection gaps can still lose incoming events. [Twitch whisper API](https://dev.twitch.tv/docs/api/reference/#send-whisper), [incoming whisper events](https://dev.twitch.tv/docs/eventsub/eventsub-subscription-types/#userwhispermessage).

## Moderator tools

Open **Moderator tools** in the browsing menu. Load the channels you moderate, or enter a channel username. From a live stream, use the player menu; on a chat message, choose **Moderate user / message** to prefill its sender and make deleting that selected message available. Hide these entries using **Settings → Chat → Moderator tools** if you do not need them.

| Native panel | Available actions |
| --- | --- |
| People | Permanent ban, timeout in seconds, unban/remove timeout, and warning with a reason. |
| Chat controls | Read current modes; change slow, followers-only, subscribers-only, emote-only and unique-chat modes; send an announcement; clear chat. |
| AutoMod | Listen for new held messages and allow/deny them; inspect category settings; set overall level 0–4; view/add/remove blocked terms. |
| Unban requests | Load pending requests and approve/deny with an optional resolution note. |
| Shield mode | Activate or deactivate the broadcaster's configured Shield settings. |

Every mutation requires confirmation. Timeouts are restricted to 1 second–14 days, warnings require a reason, and chat settings are patched one mode at a time. A failed or uncertain request is not silently repeated. Permissions, message age, queue races and channel restrictions remain Twitch's authority; an action accepted by the request fixture is not proof it will succeed for every real account.

The native AutoMod queue starts when you tap **Listen for new held messages**. It contains at most 100 newly held items, removes items handled by another moderator, and stops when you leave the foreground. Start listening again after returning. It does not backfill messages already held before connecting. Use **Open full AutoMod queue in Twitch Mod View** for older items, private-term context or gaps.

Setting an overall AutoMod level replaces custom category levels; the confirmation makes this explicit. Blocked-term and unban lists have load-more controls with duplicate-page guards. Refresh their list after changing an item to obtain current server state.

### Polls, predictions and everything else

Use **Polls → open Twitch poll manager** for the dedicated poll panel, or **Open Twitch Mod View** for polls, predictions and the full moderator workspace. These are explicit browser links, not native panels. Twitch's public poll/prediction management APIs use broadcaster authorization, so a moderator's token is not substituted or treated as the broadcaster's. [Twitch API permissions](https://dev.twitch.tv/docs/api/reference/#create-poll), [Twitch poll controls](https://help.twitch.tv/s/article/how-to-use-polls?language=en_US).

**Your browser has its own Twitch login.** Check the account shown there; switching HexStream accounts does not switch browser cookies. HexStream neither copies cookies into those panels nor forwards its login credentials. The browser Mod View route is also available from the channel picker if native API login is unavailable.

## Create a clip from someone else's live stream

Open the live player's menu and choose **Create a clip**. Tap **Capture clip now**, confirm, then open the returned Twitch editor. Choose the moment, trim/title it and publish/share through Twitch. Copying the editor link does not by itself publish a finished clip.

You do not need to follow the broadcaster or be their moderator. Twitch's channel clip restrictions still apply; clip creation needs the active account's clip permission and a live channel that allows it. The capture uses Twitch's timing window, which may differ from the delayed video on your screen. [Twitch clip creation](https://dev.twitch.tv/docs/api/reference/#create-clip).

One screen submits at most one creation request. Rotation retains the in-flight request/result; restoration after process loss warns that a clip may already exist. No second clip is created by automatic retry. If an interrupted request might have succeeded, check Twitch before starting another. Only HTTPS editor links on Twitch's exact hosts are accepted. The external editor uses your browser's account.

## Candidate smoke checks

Use an account and channel where you have permission. The automated fixtures never submit a real whisper, clip, follow, ban or other moderation action.

- Save a non-followed channel's alert; reopen its channel page, then switch accounts and confirm their alert lists remain separate.
- Reconnect API login, enable Whispers, send/receive an intentional test message, background/resume, and switch accounts. Confirm disabled Whispers disconnects and hides its menu.
- Open a channel you moderate; inspect modes and new AutoMod holds. Test timeout/unban and one warning only on an agreed test account. Check unban requests and browser poll/Mod View access.
- Capture one clip from a permitted non-followed live channel; rotate while waiting, open the editor, and check that only one request was made.
- Recheck playback, transparent/docked chat, gestures, category back navigation, global filters and proxy settings.
