# Voxa

A deliberately simple, RSS-first podcast app for Android. No search, no browse, no recommendations, no social. You paste a feed URL, your shows live in the order you choose, and there is a mini player.

## Status

Styled after the mock (cream paper, ink, lime accent, JetBrains Mono; follows system dark mode).
- Home: Podcasts (grid or list, A-Z or custom order, edit order, dashed add-RSS card), Episodes (recent across all shows), Downloads
- Show page: Episodes / About / Settings tabs, numbered episodes, oldest/newest toggle (remembered per show), download latest 5
- Episode sheet (kebab): play, download, add to queue, clear progress, remove download, hide / unhide
- Hidden episodes only appear when Settings > "Show hidden episodes" is on; downloads skip them
- Mini player, full player (seek, -10 / +30, speed, sleep timer, download, queue), position saved, played marking
- Playing an episode auto-queues the rest of that show after it in the show's sort order (skips hidden and played)
- Background refresh around 8am and 8pm (WorkManager, 12-hourly) with a notification per show for new episodes
- Player: skip silence and volume boost toggles (session commands handled in PlaybackService), Notes sheet with chapters (Podcasting 2.0 chapters JSON) and show notes
- Backup & restore from the home menu: JSON (subscriptions, order, progress, played/hidden, settings) and OPML
- Mono mix toggle (custom audio processor)
- Android Auto: Voxa is a media library (Podcasts > shows > episodes); playing from the car resumes the episode and queues the rest of the show. Sideloaded apps only show in Android Auto after enabling Developer settings > Unknown sources in the Android Auto app
Untested on a real device beyond the first UI build.

## Layout

- `data/` Room entities and DAOs, `RssParser`, `VoxaRepository`, `DownloadWorker`
- `playback/` `PlaybackService` (ExoPlayer + media session), `PlaybackConnection` (play / queue)
- `ui/` `HomeScreen`, `ShowScreen`, `EpisodeSheet`, `MiniPlayer`, `PlayerScreen`, `Components`, `Theme`, `Type`, `VoxaViewModel`

## Build

Open the `voxa/` folder in Android Studio and sync. Min SDK 29. If Android Studio asks, let it generate the Gradle wrapper. Versions are pinned in `gradle/libs.versions.toml`; bump them if Studio suggests newer ones.

## Notes

- Downloads: `DownloadWorker.enqueue(showId, scope, oldestFirst)` (latest N, all unplayed, or entire show) or `enqueueEpisode`; notification with progress and Cancel; Android 13+ asks for notification permission on first download
- Download folder: picked with the system folder picker (Storage Access Framework); each show gets a subfolder, files are `yyyy-MM-dd - Title.ext`. Asked once on the first download; change it from the home menu or a show's Settings. `Episode.localPath` is a file path (app storage) or a content:// URI (chosen folder)
- Room schema is version 2 (added episodes.chaptersUrl, migration 1->2), so builds install over each other
- Debug APK is large (~68MB) because the full Material icon set is bundled; release builds with minification shrink it

## Not done yet

1. Drag-to-reorder (edit order uses arrows)
2. Exact-time refresh (Android picks the minute for the 8am/8pm checks)
3. Queue is in-memory only (lost when the player service stops)
4. Themed (monochrome) launcher icon
5. Room migrations before you ship a schema change
