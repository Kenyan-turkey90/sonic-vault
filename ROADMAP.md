# Sonic Vault — Product Roadmap

> Living document. Priorities are labeled **P1 / P2 / P3**. Every item lists effort,
> rationale, and acceptance criteria so work can be picked up by any agent or contributor.

**Guiding principle:** Sonic Vault's edge is *engineer-first audio* — a from-scratch player
with Together live sessions, 10+ widgets, AOD, deep lyrics, and real audio DSP (EBU R128
loudness normalization, crossfade, pitch/tempo, equalizer). Everything below compounds that
edge. "Best in the world" for a music player = flawless playback, transparent audio quality,
zero-waste personalization, and delight in the details.

---

## Pillar 0 — Foundation (non-negotiable, do first)

The codebase is strong but carries legacy weight. Fix these before stacking features.

### P0.1 — Land the rename & green build
- **Effort:** M · **Status:** rename done in `Sonic Vault 2`; compile verification pending
- Build verification command (run where network works):
  ```
  ./gradlew :app:compileGmsMobileUniversalDebugKotlin :app:compileFossMobileUniversalDebugKotlin
  ./gradlew :app:testGmsMobileUniversalDebugUnitTest :app:testFossMobileUniversalDebugUnitTest
  ```
- **Acceptance:** both distributions compile; all unit tests pass; 0 refs to `moe.rukamori`/`archivetune` in source (except intentional migration constants, URLs, GPL headers).

### P0.2 — Split the monoliths
- **Effort:** L · **Files:** `MusicService.kt` (~8.4k lines), `MainActivity.kt` (~3.1k lines)
- Extract testable cores: stream resolution, queue management, Together sync/clock, crossfade engine.
- **Acceptance:** stream resolution and queue logic live in classes with unit tests; MusicService is an orchestrator, not a god object.

### P0.3 — Make tests a real gate
- **Effort:** M · CI already runs `:app:test*` + detekt on PRs (added in rename pass).
- Grow coverage: stream resolution, queues, lyrics providers, Together framing, `PlaybackStreamRecoveryTracker`, `TogetherClock` (done).
- **Acceptance:** a broken PR cannot land; detekt blocks on violations after a warning grace period.

### P0.4 — Crossfade confidence
- **Effort:** M · Crossfade machinery exists (`secondaryCrossfadePlayer`, gapless mode, slider UI).
- **Acceptance:** crossfade + gapless work reliably across stream client rotation and AOD; no audio glitches at track boundary; covered by a test harness where feasible.

---

## Pillar 1 — Audio excellence

Where "best in the world" is won for a player app.

### P1.1 — Now-playing stream badge (codec / bitrate / sample rate)
- **Effort:** S–M · Foundation already exists (`ShowMediaInfo` dialog: itag, mime, codecs, bitrate, loudness dB).
- Show a live badge on the now-playing screen (e.g., `AAC-LC · 128 kbps · 44.1 kHz`), tap → full `ShowMediaInfo`. Show "live" indicator for streams.
- **Acceptance:** badge visible while playing, reflects the actual current stream, updates on stream client change, respects reduced-motion/accessibility settings.

### P1.2 — Per-network quality presets
- **Effort:** M
- Cellular vs Wi-Fi quality presets with data-cost framing (MB/min). Auto-switch on network change; explicit override per-session.
- **Acceptance:** presets configurable in Settings → Audio; switching networks picks the preset; data-cost shown; override resets per session.

### P1.3 — In-player audio entry point
- **Effort:** S–M
- Quality, normalization, equalizer reachable from the player menu (already has pitch/tempo + equalizer; add normalization + stream info).
- **Acceptance:** all audio controls reachable in ≤2 taps from now-playing; mirrors Settings state.

### P1.4 — Loudness normalization prominence
- **Effort:** S · EBU R128 normalization is already implemented (`AudioNormalizationKey`, `calculateAudioNormalizationFactor`).
- **Acceptance:** toggle visible in player menu; reflects live normalization state; tooltip explains intent.

### P1.5 — Gapless playback
- **Effort:** L · YouTube's encoder delay/padding makes this genuinely hard; crossfade/gapless pipeline already exists.
- **Acceptance:** gapless transitions for consecutive tracks from the same source where metadata allows; configurable in Settings (exists); audibly seamless on real devices.

### P1.6 — Local hi-res + bit-perfect output
- **Effort:** L · Differentiator almost no Android player has.
- **Acceptance:** local FLAC/ALAC files output at native sample rate/bit depth on supported DACs; sample-rate switching handled gracefully; fallback when unsupported.

---

## Pillar 2 — Playback reliability (what users churn on)

Strong resilience already exists (client fallback rotation, po-token/visitor-data refresh,
bot-detection recovery, chunk resolution). Level up.

### P2.1 — Smart offline
- **Effort:** L
- Separate download vs streaming quality; Wi-Fi-only-by-default downloads; "smart offline" auto-caches Liked songs and frequently played playlists.
- **Acceptance:** settings for quality + Wi-Fi gate; smart-offline toggle; storage budget respected; cache eviction sensible.

### P2.2 — Playback diagnostics screen
- **Effort:** M
- Power users see *why* a stream failed: client used, HTTP status, error class, retry timeline, po-token age. Becomes your QA team.
- **Acceptance:** screen reachable from player menu + Settings; last N failures logged; copy-to-clipboard support.

### P2.3 — Instant resumption
- **Effort:** M
- Cold start → playing in under ~1 s from notification/widget/deep link.
- **Acceptance:** median cold-start-to-audio time measured and improved; widget + notification taps resume without flicker.

### P2.4 — Stream reliability hardening
- **Effort:** M · Extend `PlaybackStreamRecoveryTracker` with telemetry and smarter retry backoff.
- **Acceptance:** recovery decisions covered by unit tests; retry storms avoided; failures attributed to root cause.

---

## Pillar 3 — Personalization (from your own stats DB)

Play counts, listening-by-slot, and Year-in-Music already exist — a recommendation engine waiting to happen, with zero network calls.

### P3.1 — Antiskip-weighted autoplay
- **Effort:** M
- Deweight tracks you skip; autoplay gets "smarter" locally. Uses existing listen/skip stats.
- **Acceptance:** autoplay queue order measurably avoids skipped tracks; still respects manual queue edits; toggleable.

### P3.2 — Smart autoplay from listening habits
- **Effort:** L
- Time-of-day / day-of-week aware suggestions from listening-by-slot data ("your Friday afternoon mix").
- **Acceptance:** generated playlists surface on Home; generated from local stats only; regenerable.

### P3.3 — Year-in-Music depth
- **Effort:** M · Year-in-Music exists (player anchor handling already in code).
- Add: top genres, listening streaks, "your most repeated 10 seconds" moments, shareable cards.
- **Acceptance:** share cards export clean images; stats accurate vs DB; no new permissions.

---

## Pillar 4 — Together (the moonshot that could make it famous)

### P4.1 — Together web companion
- **Effort:** L
- Web/desktop companion to view the room, see the queue, vote on next track, and chat — no app install required.
- **Acceptance:** join from QR/link; real-time queue + chat via existing Together protocol; guest vs host permissions.

### P4.2 — Together v2 protocol
- **Effort:** L
- Streamlined framing, reconnection with clock resync (`TogetherClock` exists + tested), host migration, latency display.
- **Acceptance:** host loss doesn't end the room; offsets converge after reconnect; covered by `TogetherClockTest`.

### P4.3 — Together social layer
- **Effort:** L
- Rooms for friends; public "listening rooms" discovery; reaction emoji; per-member now-playing badges.
- **Acceptance:** room invites work; reactions propagate in < 1 s; moderation basics (mute/kick).

---

## Pillar 5 — Discovery & content

### P5.1 — Search unification
- **Effort:** M
- Unified results across YouTube, your library, local files, downloads, and lyrics with ranking + filters.
- **Acceptance:** one search box returns everything; tabbed or ranked results; fast with large libraries.

### P5.2 — Home feed intelligence
- **Effort:** M
- Home sections blend online charts with your stats: "Since last week", "Your re-listens", "Try something new" (deweighted genres).
- **Acceptance:** sections use local stats; manual refresh + "hide this" per section.

### P5.3 — Radios that respect taste
- **Effort:** M · Radio endpoints exist (`YouTubeQueue.radio`, `YouTubeAlbumRadio`).
- **Acceptance:** radio seeds from multiple songs; "tuner" (more/less familiar); saves as playlist.

---

## Pillar 6 — Widgets, AOD & surfaces (already a differentiator — polish it)

### P6.1 — Widget quality pass
- **Effort:** M
- Consistent design language across 10+ widgets; tap-targets; dynamic color; configuration options (art style, show/hide progress).
- **Acceptance:** all widgets themed consistently in light/dark; configs persisted; no jank on 60 Hz + 120 Hz.

### P6.2 — AOD polish
- **Effort:** S · AOD mode exists (`aodModeEnabled`, status-bar hiding, screen-on flags).
- **Acceptance:** AOD gesture entry consistent; exit path reliable; no flicker; battery impact documented.

### P6.3 — Media-session & notification polish
- **Effort:** S
- Accurate timestamps, artwork transitions, and playback-state reporting to all surfaces (lock screen, Wear, auto).
- **Acceptance:** every surface shows correct state within 200 ms of a change.

---

## Pillar 7 — Quality of life & delight

### P7.1 — Smarter queue UX
- **Effort:** M
- Queue: drag-to-reorder with haptics, "play next vs add to end" long-press, queue search/filter, per-item "why is this here".
- **Acceptance:** all operations covered by unit tests where logic-based; reorder persists across session restore.

### P7.2 — Playback timers & fades
- **Effort:** S
- Sleep timer (end of track / fixed duration) with optional volume fade-out; pause-when-blet-disconnects (exists for some surfaces — unify).
- **Acceptance:** timer survives service restart; fade-out implemented; notification shows remaining time.

### P7.3 — Share & social proof
- **Effort:** S
- Share tracks/playlists/radios with rich cards; "what I'm listening to" sharing; deep-link handling for shared content.
- **Acceptance:** shared links open correct content in-app; card art renders; fallback for non-SonicVault recipients.

### P7.4 — Enhanced lyrics everywhere
- **Effort:** M · Multi-provider lyrics + AI translation exist.
- **Acceptance:** karaoke-style sync improvements; chord support for local files; better multi-language display; lyrics survive stream-client rotation.

---

## Pillar 8 — Engineering & trust

### P8.1 — Performance budget
- **Effort:** M
- Profile list scroll jank with Compose metrics (`enableComposeCompilerReports`); set a CI budget; fix hot recompositions in Home/Player.
- **Acceptance:** measurable 60 fps on mid-tier devices for Home, Library, and Player scroll; CI fails on regression.

### P8.2 — Accessibility pass
- **Effort:** M
- Full content descriptions, font-scaling test pass, TalkBack through the whole player, contrast in all themes.
- **Acceptance:** automated + manual accessibility checks pass for the 5 core screens.

### P8.3 — Security & secrets hygiene
- **Effort:** S
- Ensure keystore (`Koiverse.jks`) and local.properties are never tracked; document submodule + secrets setup for maintainers.
- **Acceptance:** repo clean of credentials; CONTRIBUTING/README documents required secrets (e.g., `START_IO_APP_ID`, `LASTFM_API_KEY`).

### P8.4 — Test coverage on critical paths
- **Effort:** M
- Stream resolution, queue persistence/restore, Together framing, lyrics provider parsing, offline cache eviction.
- **Acceptance:** each listed path has ≥1 meaningful unit test; CI runs them.

---

## Themed extras (small, high-impact)

- **E1 · Now-playing info tap-to-copy** — copy stream URL/ID from `ShowMediaInfo` (partly exists). *(S)*
- **E2 · Playback speed in player** — one-tap 0.5×–2.0× cycle on the now-playing screen. *(S)*
- **E3 · Artist/album shimmer placeholders** — skeleton loading to kill perceived latency on Home. *(S)*
- **E4 · Quick quality chip in player** — same as P1.3/P1.1 combined chip. *(S)*
- **E5 · Notification expand actions** — like/playlist/add-to-queue from the notification itself. *(S–M)*
- **E6 · Battery-friendly streaming** — cap bitrate when battery < 20% + screen off (opt-in). *(S–M)*
- **E7 · Listening streak counter** — show days-in-a-row streak in Year-in-Music / stats. *(S)*
- **E8 · Local file tags editing** — edit title/artist/album/art for local files. *(M)*

---

## Suggested execution order

1. **P0.1** (verify build) → **P0.3** (tests gate) → **P0.2** (split monoliths, incrementally)
2. **P1.3/P1.4 + E4** (in-player audio: normalization toggle, stream badge) — quick wins, immediately visible
3. **P3.1** (antiskip autoplay) — pure local-logic, high perceived value
4. **P2.2** (playback diagnostics) — turns users into QA
5. **P1.2** (per-network presets) → **P2.1** (smart offline)
6. **P4.x** (Together) as the moonshot track, run in parallel once the core is stable

---

## Status legend

- **Not started** — queued, no code yet
- **In progress** — work underway
- **Done** — implemented and verified

*Last updated: 2026-08-16 (rename pass + startup fix + CI + first tests landed).*
