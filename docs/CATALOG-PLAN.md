# Catalog refinement

## Scope

- Phone: responsive poster grid, scrollable title hero, TMDB logos, scores and episode stills; preserve categories, global search and twenty-title pages.
- TV: same APK, safe margins, adaptive landscape layout, remote focus and reachable Back/Play controls.
- Desktop/web: unchanged in this Android-specific pass; reuse the existing shared metadata and image components.
- Playback: resolve the delivered message in the signed-in user's bot conversation, then use the original MediaMapper and PlayerActivity streaming path.

## Edge cases

Missing metadata keeps database titles and playable entries. Empty pages and unavailable episodes have explicit states. Search is debounced. Retry repeats the same page. Playback requests cannot overlap; a delivered message is retried without sending another video. Network work is bounded and cancellation propagates. Episode lists preserve season and paginate twenty rows. No file download is introduced; code edits require under 1 MB, with about 77 GB free at inspection.

## Progress / handoff

Loading regression: the loading effect awaited grid.scrollToItem before querying the API, but the grid was only composed after loading completed. Removed that pre-layout wait and the redundant pager scroll. The saveable grid state is now keyed by category, page and search, resetting position without suspending the request. Loading failures show retry and cancelled requests do not clear a newer request's loading indicator. Added regression tests for initial loading, page changes and retrying the same page. Source reviewed; Android compilation and these tests still require the VPS toolchain.

Reference inspected: exemplo MediaDetailScreen, EpisodeRow, Meta and original MediaMapper/PlayerActivity.
Implemented: shared metadata/image pipeline, title hero and logos, scores and trailer, season guide/stills, responsive dark layout, explicit TV focus, saved catalog selection, debounced search, bounded pages, playback progress/cancellation and same-delivery retry.

Playback now matches a server-generated tag in the raw caption in the recipient's bot chat. The original mapper removes the hashtag for display and preserves content restrictions before PlayerActivity takes over. No Bot API message id is used to query another account. The bot no longer simulates successful delivery when disconnected.

Checks completed locally: six isolated bot route tests passed; JavaScript syntax check passed; English and Brazilian Portuguese JSON and key ordering passed; source inspection of the original mapper/provider/player contracts completed.

Android compilation and Kotlin/Robolectric tests are NOT run here: only Java 8 is installed, and the local Gradle distribution is incomplete. Added garbage-input coverage for delivery tags and phone/TV layout, screenshot and D-pad tests for the VPS toolchain. Device playback, visual parity, rotation during delivery, font scaling and TV idle CPU remain manual validation tasks. Other languages currently fall back to English for the new catalog strings; translate before a multilingual release.

## VPS handoff

Upload the changed app and core sources/resources and bot/api.js. New CatalogMetadata.kt, CatalogPlayback.kt and core CatalogDeliveryTag.kt must be included. Do not copy exemplo over the app.

Restart the existing bot process (the one that initializes api.js with the live bot). Starting api.js alone intentionally returns HTTP 503 for playback.

Run in the project root with the configured JDK/SDK:

```sh
node --test bot/tests/catalog-play.test.js
./gradlew :core:jvmTest :app:testDebugUnitTest --tests 'com.tmplayer.catalog.CatalogLayoutTest' --no-daemon
./gradlew assembleDebug --no-daemon
```

The layout test writes WebP previews to app/build/catalog-previews. Install the new APK on phone and TV. Check a film and an episode, Back from the player, search, page/season changes, missing metadata, offline retry and D-pad navigation. A server still running the older API produces an explicit update-API message.
