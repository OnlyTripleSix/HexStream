# Credits and provenance

## Xtra comes first

**HexStream is built on Xtra.** It is not a Twitch client written from scratch.

- **Andrey Asadchy** created the original [AndreyAsadchy/Xtra](https://github.com/AndreyAsadchy/Xtra).
- **crackededed and Xtra contributors** continued development in [crackededed/Xtra](https://github.com/crackededed/Xtra), the upstream used here.
- **TripleSix** maintains the HexStream customization and branding.

The starting source is Xtra **2.60.7**, upstream commit [`a3cbb0325f3330573a6738f7d36a2c5b785f4d1c`](https://github.com/crackededed/Xtra/commit/a3cbb0325f3330573a6738f7d36a2c5b785f4d1c). The public HexStream branch begins with a source snapshot; upstream author history remains available in those linked repositories. The original upstream README is retained in [docs/upstream/README-Xtra.md](docs/upstream/README-Xtra.md).

Beta.3 incorporates **crackededed’s Xtra 2.60.8 fixes**, through upstream commit [`96772030c7712581ad150c9b8b619c77a3527e50`](https://github.com/crackededed/Xtra/commit/96772030c7712581ad150c9b8b619c77a3527e50). The [upstream synchronization record](docs/upstream/SYNC.md) links each original commit and describes adaptations for HexStream.

Beta.7 incorporates **crackededed's Xtra 2.60.9 Helix timeout fix** and **LizenzFass78851's German translation improvements**, through [`0444de816e28de1654979a5a2a663b3fc51cb6a6`](https://github.com/crackededed/Xtra/commit/0444de816e28de1654979a5a2a663b3fc51cb6a6). Their original commit links, authorship and HexStream's compatibility adjustments are preserved in the same synchronization record.

Xtra provides the app architecture and much of the player, Twitch integration, chat, emote support, browsing, VOD/clip handling, downloads, saved content, themes and settings. HexStream’s changes focus on category pagination, global content controls, saved-account switching, global proxy presets, live-delay tuning, gestures, points/reward handling, reliability and branding.

## Filtering inspiration: Unwanted Twitch

**[Unwanted Twitch by kwaschny](https://github.com/kwaschny/unwanted-twitch)** inspired HexStream’s channel/category/title/tag blocking workflow and compatible blocking-list JSON format. Credit belongs to its author and contributors for establishing that approach.

HexStream implements its own native Android filtering engine. The separate [HexStream Filter browser extension](https://github.com/OnlyTripleSix/HexStream-Filter) is also independently implemented and credits Unwanted Twitch’s inspiration and format. No Unwanted Twitch source code or artwork is bundled in either project.

## Floating chat: ThystTV

HexStream's fullscreen chat overlay adapts the overlay and **same-view reparenting** approach in **[ThystTV](https://github.com/tzii/ThystTV)**, by **tzii (Simone Perini) and ThystTV contributors**. Their work on preserving a chat session while moving it between the sidebar and the video, readable text over video, sizing and positioning informed this feature.

The reviewed source snapshot is [`ae3b1c6c784c1ebc4b8096c3a8df5791827787e4`](https://github.com/tzii/ThystTV/tree/ae3b1c6c784c1ebc4b8096c3a8df5791827787e4), licensed under GNU AGPL v3. The [adaptation record](docs/upstream/THYST-CHAT.md) links the relevant original changes. HexStream retains the existing Xtra foundation and credits above; ThystTV is also credited in the app's About screen.

## License

The app source is distributed under the **GNU Affero General Public License, version 3**, following Xtra’s license. See the complete [LICENSE](LICENSE). Keep upstream copyright and license notices when redistributing or modifying the source. Corresponding source for each published build is available at its release tag.

Dependency components and included assets can have their own licenses. Existing source headers and third-party notices are retained; the app license does not replace them. In particular, the Unicode emoji data notice is at [`UNICODE-LICENSE.txt`](app/src/main/assets/UNICODE-LICENSE.txt), and Media3-derived resources retain their applicable notices.

## Other projects and services

- [AndroidX](https://developer.android.com/jetpack/androidx), including Paging, Room, WorkManager and Media3; [Kotlin](https://kotlinlang.org/), kotlinx libraries, [Apollo Kotlin](https://github.com/apollographql/apollo-kotlin), [OkHttp](https://github.com/square/okhttp), [Cronet](https://chromium.googlesource.com/chromium/src/+/main/components/cronet/), [ML Kit](https://developers.google.com/ml-kit), image libraries and other dependencies listed in [`libs.versions.toml`](gradle/libs.versions.toml).
- [7TV](https://7tv.app/), [BetterTTV](https://betterttv.com/) and [FrankerFaceZ](https://www.frankerfacez.com/): third-party emote ecosystems supported through Xtra’s integrations.
- Luminous and PerfProd: independent operators of the preloaded playback endpoints. Inclusion does not imply affiliation, endorsement or guaranteed availability.

Twitch names, content and services belong to their respective owners. HexStream is an independent client and is not affiliated with Twitch or Amazon.
