![SpatialFlow Update](https://media3.giphy.com/media/v1.Y2lkPTc5MGI3NjExZmhicDgzcDhybm5qM3Z1bWY0M2Y0YnMyamF5b2NvaXYzbGl5Njl1byZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/Ujb2pmjn2v98Np0iPd/giphy.gif)

## 🚀 What's New in SpatialFlow v1.8.2 — "Gigachad Update 🗿🍷"

### New Features & Enhancements

- **Apple Music-Style Karaoke Lyrics:** Word-by-word GPU smoothstep sweep shader with vocal swell physics and organic AGSL film grain backdrop.
- **Dynamic Background Vocals:** Syllable-accurate sweeping without mid-word freezing, responsive 350 ms spring entry, active sweep continuation, and expressive spring exit animations.
- **Bulletproof TTML & XML Sanitization:** Pre-sanitizes unescaped ampersands and malformed tags; suppresses raw XML fallthrough to permanently prevent code leaks on screen.
- **Real-Time Floating Sync Bar:** Dynamic offset controls (-0.5s, +0.5s, ms steps) directly on the lyrics view for live synchronization adjustments.
- **Auto-Hiding Lyrics Controls:** Playback controls smoothly fade after 3 seconds of inactivity, allowing lyrics to expand seamlessly to full screen.
- **Native 32-Bit Float DSP & Bit-Perfect Engine:** Parametric EQ, Dolby Atmos binaural spatialization, and USB DAC bit-perfect bypass.
- **Background Playback Stability:** High-performance partial wake locks and Wi-Fi locks in AudioPlaybackService to prevent playback halts during sleep or screen lock.
- **Track-Isolated Local Audio Artwork:** MediaStore thumbnail extraction (API 29+) and embedded ID3/Vorbis parsing in AudioArtworkFetcher, eliminating cover bleed across untagged files.
- **SpatialFlow Live (Listener Presence):** Lightweight Cloudflare Worker integration showing live listener counts with an icon-only M3 bottom sheet and privacy toggle.
- **Material 3 Expressive & Liquid Glass:** Kyant Backdrop liquid glass refraction, Chris Banes Haze blur, interactive floating nav bar, and pushing neighbor button groups.
- **Canvas Video Backdrops & Transitions:** 36x downscaled canvas video engine with 4 authentic transitions (Shuffle, Push, Flip, Crossfade).
- **YouTube Music 2025 Recap Stories:** Redesigned story experience with Room database migration (5 -> 6) and ANR elimination.
- **Last.fm Native Scrobbler:** Real-time scrobbling with offline database queue and loved tracks sync.
- **Spotify In-App Login:** Secure WebView authentication and curated Spotify explore feeds.
- **Responsive Tablet Explore Layout:** Proportional sizing on Spotify Explore banners for tablets and large displays.

### Bug Fixes & Stability

- **Playback Halts:** Fixed playback stopping abruptly in background due to OEM battery management.
- **Lyrics Freezing:** Fixed background vocals freezing mid-word (e.g. "(Yeah, yeah, yo)") during sweeps.
- **XML Code Leaks:** Suppressed raw `<tt>` markup from displaying when lyrics providers return malformed responses.
- **Background Vocals Missing Exit:** Added 350 ms spring exit animation when backing vocal phrases end.
- **Background Vocals Premature Visibility:** Kept background vocals hidden on upcoming lines until their active cue.
- **Local Album Cover Bleed:** Prevented MediaStore generic album ID collisions across untagged local files.
- **Spotify Banner Distortion:** Added responsive layout bounds for tablet and foldable displays.
- **Duplicate Launcher Icons:** Cleaned redundant `<intent-filter>` declarations in Manifest.
- **LazyColumn Key Collision:** Eliminated crash when playlists contained duplicate track IDs.
- **Copyright Notice:** Updated copyright notice to © 2026 Shubham Karande in Settings.

### Improvements & Refactoring

- **Codebase Modularization:** Decomposed monolithic FullPlayer and SettingsFragment into clean domain modules.
- **Clean Image Architecture:** Centralized local artwork extraction in AudioArtworkFetcher with Coil keying and reverted SpatialImage to pure Compose shimmer.
- **Community Contributors:** Added Contributors showcase in Settings About section honoring 4nx3b (yuki) and vossgraves.
- **Purged Dead Code:** Removed unused unit tests, deprecated XML splash layouts, and legacy project references.

---

### Important Notes

> Dynamic Theme requires Android 12+ for full Material You color extraction.

> This update includes a database migration (Room 5 -> 6) — please allow the initial launch to complete seamlessly.

> Clear app cache in Settings -> Storage -> Clear Image Cache if you experience any UI artifacts after upgrading.
