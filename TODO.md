# Sonic Vault — Task TODO

## 1. Video playback — DECISION: removed (audio-only app)
- [x] Removed the Song/Video toggle UI (v14.0.11).
- [x] Removed residual video-mode machinery from `MusicService.kt` and `YTPlayerUtils.kt`
      (`reloadCurrentTrackWithVideoMode`, `videoOnlyPlaybackUrl`, `VideoModeEnabledKey`, merged video caches).
- [x] `HideVideoKey` (hide videos from library) kept — separate, active feature.
- [ ] If video playback is ever revived, re-add via `playerResponseForPlayback(video = true)` +
      a per-track override; stream selection and merged-source logic still exist as a reference.

## 2. Replace app icon & logo
- [x] Launcher icons already regenerated from design assets (Aug 8); fixed ic_launcher_monochrome to a proper white silhouette
- [ ] Regenerate `about_splash.png`, `about_appbar.png`, `app_icon_small.png`.
- [ ] Keep `small_icon.xml` unchanged.

## 3. Startup UX — open on Home for a new session
- [x] Fixed: a fresh process start now always opens the Home page with the mini player collapsed
      (`MainActivity.kt` — `hasRestoredMiniPlayerAnchorInProcess` process-lifetime guard).
      Warm restarts (rotation/config change) still restore the player sheet as before.

## 4. Audio-quality roadmap (from docs/swarm research) — full plan in `ROADMAP.md`
- [ ] Now-playing stream-format badge (codec/bitrate of the current stream). Foundation exists (`ShowMediaInfo` dialog).
- [ ] Per-network quality presets (cellular vs Wi-Fi) with data-cost framing.
- [ ] Antiskip-weighted autoplay (P3.1 — needs skip-stats DB integration).
- [x] In-player audio entry point — loudness normalization toggle added to the player menu
      (`PlayerMenu.kt`, bound live to `AudioNormalizationKey`). Equalizer + tempo/pitch already there.

## 5. Engineering health
- [x] Unit tests: `PlaybackStreamRecoveryTrackerTest` + `TogetherClockTest` added; CI `quality` job wired (test + detekt).
- [x] Detekt wired into the build (all subprojects, non-blocking).
- [ ] Verify both distributions compile + tests pass once the Gradle distribution download completes (network-blocked in this environment).
