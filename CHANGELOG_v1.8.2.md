# SpatialFlow v1.8.2 — Comprehensive Changelog & Release Notes

> **Version:** `1.8.2` (VersionCode `10`)  
> **Previous Version:** `1.8.1` (VersionCode `9`)  
> **Target SDK:** Android 16 (API 36) | **Compile SDK:** API 37 | **Min SDK:** Android 7.1 (API 25)  
> **Release Date:** October 2026  
> **License:** MIT | **Author:** Shubham Karande  

---

## 🌟 Executive Summary

SpatialFlow **v1.8.2** is a monumental milestone release, consolidating months of architectural refinement, user-reported bug fixes, performance optimizations, and feature enhancements across every layer of the application. 

This release focuses on audiophile-grade audio fidelity, an authentic Apple Music-level karaoke lyrics experience with full background vocal lifecycles, complete resolution of Android MediaStore album cover bleed bugs, real-time listener presence via **SpatialFlow Live**, full Material 3 Expressive and Liquid Glass design system integration, and official preparation for GitHub and **Fastlane** distribution.

### Key Milestones at a Glance:
1. **Audiophile 32-Bit Float DSP & Bit-Perfect Engine:** Native C++ DSP pipeline with Parametric EQ, Dolby Atmos binaural spatialization, True Peak limiting, and external USB DAC bit-perfect bypass.
2. **Background Playback Stability & Sleep Prevention:** Eliminated playback stopping during background streaming through high-performance partial wake locks, Wi-Fi locks, and proactive network retry mechanisms.
3. **Apple Music-Grade Karaoke Lyrics & Typography:** Smoothstep GPU sweep shaders, vocal swell physics, real-time floating synchronization controls, AGSL film grain backdrop filters, and comprehensive multi-format support (LRC, TTML, SRT, VTT, QRC).
4. **Dynamic Background Vocals Lifecycle:** Syllable-accurate sweeping without mid-word freezing, responsive Material 3 spring entry animations, continuous active sweeps across line focus transitions, and expressive spring exit animations.
5. **Bulletproof TTML / XML Lyrics Parser:** Automated XML pre-sanitization, regex fallback parsing, recursive JSON envelope unpacking, and zero raw `<tt>`/XML code leaks on screen.
6. **Track-Isolated Local Audio Artwork Engine (`AudioArtworkFetcher`):** Dedicated track-isolated Coil pipeline using hardware-accelerated MediaStore thumbnail loading (API 29+) and direct embedded ID3/Vorbis extraction, completely fixing album art bleed across untagged local files.
7. **SpatialFlow Live (Real-Time Listener Presence):** Cloudflare Workers integration providing live listener counts, status viewer, privacy opt-in/opt-out toggles, and an icon-only Material 3 Expressive bottom sheet.
8. **Canvas Motion Video Backdrops & Transitions:** 36× downscaled real-time blurred canvas video backgrounds with Spotify/Apple Music canvas integration, dynamic palette color synchronization, and 4 authentic album art transitions (Shuffle, Push, Flip, Crossfade).
9. **Material 3 Expressive & Liquid Glass System:** Kyant Backdrop liquid glass optics, Chris Banes Haze real-time blur, interactive floating navigation bar (`SpatialFloatingNavBar`), pushing neighbor button groups, and responsive tablet / large screen explore layouts.
10. **YouTube Music 2025 Cinematic Recap:** Comprehensive story deck overhaul, dynamic palette color harmonization, Room database schema migration (5 $\to$ 6), ANR deadlock elimination, and Instagram/social share card export.
11. **Last.fm, Spotify & InnerTube 2026:** Native Last.fm scrobbler with offline database queue, in-app Spotify WebView authentication, and modernized 2026 InnerTube stream extraction.
12. **Codebase Cleanup, Contributors & Distribution:** Modularization of monolithic files (`FullPlayer.kt`, `SettingsFragment.kt`), removal of legacy app references and dead test code, Contributors showcase in Settings, and full Fastlane release packaging.

---

## 🎧 1. Audio Engine, DSP & Playback Architecture

### Native 32-Bit Float C++ DSP & Bit-Perfect Pipeline
- **Studio-Grade DSP Processing:** Integrated high-performance 32-bit floating-point DSP engine with Parametric EQ, Spatial Binaural processing, and True Peak Limiting.
- **Filter Memory Explosion & NaN Guard:** Guarded native biquad filters against NaN and infinity poisoning during rapid track skips and sample rate transitions.
- **Race Condition & Singleton Elimination:** Resolved dual-player crossfading state desynchronization in `CrossfadePlayerManager` where DSP filters failed to re-engage after track changes.
- **External USB DAC Bit-Perfect Support:** Implemented `DacManager` routing that bypasses Android's native AudioFlinger resampler and mixer when external DAC hardware is detected.
- **Live Animated Sine Wave Visualizer:** Introduced `AnimatedSineWave` in the M3 Expressive `DacCapabilitiesBottomSheet.kt`, replacing static indicators with live real-time audio wave rendering.
- **DAC Connection Notification:** Added real-time non-intrusive banner (`DacConnectedBanner.kt`) signaling bit-perfect status and active hardware sample rates.

### Background Playback Stability & Sleep Prevention
- **Eliminated Playback Halts:** Addressed user reports of playback mysteriously stopping after prolonged playback or screen lock by reinforcing `AudioPlaybackService.kt`.
- **Power & Wi-Fi Wake Locks:** Acquired `PowerManager.PARTIAL_WAKE_LOCK` and `WifiManager.WIFI_MODE_FULL_HIGH_PERF` during active playback, preventing OEM battery-saver daemons and Android Doze mode from killing audio decoder threads.
- **Foreground Service Resilience:** Reinforced persistent foreground service notifications with active media session tokens and explicit restart flags (`START_STICKY`).
- **Resilient Network Stream Retry:** Built exponential backoff recovery in `StreamClientUtils.kt` to silently handle transient TCP/TLS drops without stalling the playback pipeline.

### Track-Isolated Local Audio Artwork Engine (`AudioArtworkFetcher`)
- **Eliminated MediaStore Cover Bleeding:** Resolved the longstanding Android MediaStore album thumbnail cache poisoning bug where untagged or single local tracks were grouped under generic album IDs (`albumId = 5`), causing dozens of tracks to display the same cover.
- **Hardware-Accelerated Track Extraction:** On Android 10+ (API 29+), `AudioArtworkFetcher` loads thumbnails directly from each song's individual audio `contentUri` via `ContentResolver.loadThumbnail`.
- **Direct Embedded ID3 / Vorbis Cover Extraction:** For pre-Android 10 devices or direct file paths, reads embedded artwork directly from the track's `ParcelFileDescriptor` using `MediaMetadataRetriever.embeddedPicture`.
- **Strict Generic Album Prohibition:** Prohibits fallback to generic album names (e.g., `"Download"`, `"Music"`, `<unknown>`), ensuring untagged tracks display the clean Material vector music note instead of neighboring covers.
- **Coil Pipeline Integration:** Registered `AudioArtworkFetcher.SongItemMapper` and `AudioArtworkFetcher.SongItemKeyer` in `SpatialFlowApplication.kt`, establishing track-isolated memory and disk caching (`song_art_${data.id}_...`).
- **Clean UI Decoupling:** Stripped all local song logic from `SpatialImage.kt`, keeping UI image components unopinionated, lightweight, and centered around pure Compose shimmer animations.

### Audible Dolby Atmos & 3D Spatial Audio Engine
- **Dedicated Spatialization Pipeline:** Created `DolbyAtmosAudioProcessor.kt` and `DolbyAtmosDetector.kt` to deliver binaural 3D virtualization and spatial depth across stereo headphones.
- **Safe Signal Fallbacks:** Ensured strict safety bypass to protect native bit-perfect streams when spatialization effects are disabled.

### Lossless, Hi-Res & Multi-Source Stream Resolution
- **Lossless Provider Escalation:** Expanded stream resolution engine to fetch true lossless audio from Tidal HiFi Plus, Qobuz Studio, Deezer (`DeezerCrypto.kt`), and ArchivePool / MLS Kouzu.
- **Sample Rate Resolution up to 192 kHz:** Replaced hardcoded 96 kHz caps with dynamic metadata negotiation (`HI_RES_LOSSLESS` $\to$ `HI_RES` $\to$ `LOSSLESS`).
- **Force Lossless & Force OPUS Controls:** Added dedicated toggles in Playback Settings with Material 3 Expressive "Beta" badges (`BetaPill.kt`), allowing users to force maximum bitrate streams.
- **Accurate Container Bitrate Detection:** Fixed ExoPlayer container header extraction in `AudioPlaybackService.kt` to reflect true real-time bitrates (e.g., FLAC 1411 kbps, AAC 256 kbps, OPUS 160 kbps).

### Streaming Resilience & Infinite Skip Loop Fix
- **Eliminated Runaway Playlist Skipping:** Implemented `SkipDebouncer.kt` and `QueuePreloader.kt` to immediately halt runaway track-skipping loops during intermittent connectivity.
- **HTTP 403 Forbidden Prevention:** Built dynamic Google Video header forging in `StreamClientUtils.kt` for Media3 `OkHttpDataSource`, bypassing strict YouTube age gates and IP token expiry.
- **NewPipe Stream Extractor Modernization:** Rewrote stream scoring to $O(N)$ zero-allocation inspection with coroutine concurrency and OkHttp HTTP/2 connection pooling.

### Intelligent Crossfade Engine
- **Decoupled Crossfade Architecture:** Separated crossfade lifecycle from `AudioPlaybackService` into `CrossfadePlayerManager.kt`.
- **Equal-Power Volume Curves:** Transitioned to sine/cosine equal-power crossfading curves, eliminating center volume dips during track transitions.
- **Bit-Perfect Safety Bypass:** Automatically disables crossfade when external USB DAC bit-perfect mode is active to prevent sample rate conversion.

---

## 🎤 2. Karaoke Lyrics & Typography Engine

### Pure Apple Music-Style Lyrics Rendering
- **Smoothstep Shader Sweep:** Implemented word-by-word GPU smoothstep progress interpolation in `KaraokeLyricsView.kt`, replicating Apple Music's fluid glowing text sweep.
- **Dynamic Vocal Swell & Sweep:** Added scale and luminance swell physics for extended vocal notes based on lyric duration metadata.
- **Physics-Driven Focal Anchor Glide Scroll:** Replaced standard linear scrolling with spring-driven focal anchor scrolling.
- **Pure White Instrumental Break Counter:** Added clean glowing circular progress dots with real-time countdown timers for musical interludes.
- **Word-Level Glow Alpha Dissolve:** Eliminated abrupt shadow pop-out by applying smooth animated alpha fades as words finish sweeping.

### Dynamic Background Vocals Lifecycle (Entry, Sweeping & Exit Animations)
- **Fluid Syllable-Accurate Word Sweeping:** Completely resolved mid-word freezing bugs (e.g., `Yeah, y` lit and `eah, yo)` dim) by replacing character offset subtraction with direct token-based mapping and proportional syllable distribution in `KaraokeLyricsView.kt`.
- **Cross-Line Active Sweep Completion:** Active words now complete their gradient sweeps smoothly even across line focus transitions, preventing backing vocals that extend past the main vocal from clipping abruptly.
- **Dynamic Spring Entry Animation:** Backing vocals attached to primary lyrics remain completely hidden until $350\text{ ms}$ before their vocal cue (`pos >= bgStartMs - 350L`), entering with an expressive M3 spring transition (fade-in, vertical expansion, slide-in, and scale-in).
- **Expressive Spring Exit Animation:** When backing vocals finish singing, `isBracketVisibleState` transitions to false after $350\text{ ms}$ (`pos >= bgEndMs + 350L`), smoothly shrinking vertically and fading out upwards into the line.
- **Full Lyric Sheet Reading Mode:** When user manually touches or scrolls the screen (`isUserInteracting == true`), all backing lyrics dynamically remain visible so users can read the entire song sheet without missing lyrics.
- **Standalone Backing Vocal Protection:** Standalone lines without primary vocal text (`mainContent.isBlank()`) remain visible to prevent zero-height collapse or list jumping.

### Bulletproof TTML / XML Parser & Raw Markup Elimination
- **Zero Raw XML Leaks:** Completely suppressed XML fallthrough in `LrcParser.kt`. If TTML/XML content fails parsing, it is never passed to plain LRC parsers, permanently eliminating raw `<tt xmlns="...">`, `<body>`, and `<p>` tags from displaying on screen.
- **Intelligent XML Pre-Sanitization:** Added `sanitizeXml` in `TtmlParser.kt` to auto-repair unescaped ampersands (`&` $\to$ `&amp;`), fix unclosed `<br>` tags (`<br/>`), and strip non-printable ASCII control characters and UTF-8 Byte Order Marks (BOM).
- **Resilient Regex Fallback Parser:** Integrated `parseWithRegexFallback` inside `TtmlParser.kt` to successfully extract lyrics lines, child spans, timestamps, and vocal roles even from malformed or truncated XML payloads.
- **Recursive JSON Envelope Extraction:** Built `extractTtmlFromJson` to unpack nested JSON responses (`{"lyrics": "<tt..."}`) returned by upstream lyrics providers.
- **Plain Lyrics Sanitizer:** Added `sanitizePlainLyrics` in `PlayerLyricsStateController.kt` to strip HTML/XML tags and unpack JSON structures before passing text to unsynced lyrics views.
- **Parser Sanitization & Validation:** Verified XML sanitization, background vocal bracket extraction, and regex fallback parsing across malformed TTML and LRC streams.

### Auto-Hiding Playback Controls on Lyrics Screen
- **Smart Inactivity Fade:** Playback controls automatically fade out smoothly after 3 seconds of user inactivity.
- **Seamless Lyrics Expansion:** When controls fade, lyrics instantly occupy the freed screen space without layout jank or white space boxes.
- **Immediate Wake on Scroll or Tap:** Any touch or scroll gesture immediately restores transport controls with fluid spring animation.

### Atmospheric Film Grain Shader
- **GPU AGSL RuntimeShader:** Added Apple Music-style animated organic film grain overlay for Android 13+ (API 33+) devices (`FilmGrainFilter.kt`).
- **Tiled BitmapShader Fallback:** Ensured high-performance noise emulation on devices running Android 7.1 to 12 (API 25–32) without CPU overhead.

### Real-Time Floating Synchronization Controls
- **FloatingLyricsControls.kt:** Embedded a real-time floating offset bar on the lyrics screen allowing users to adjust lyric timing (`-0.5s`, `+0.5s`, or fine ms increments) while watching active animations live.
- **Compact Provider Selector:** Redesigned lyrics provider bottom sheet so top lyrics remain fully visible and playable during provider selection.

### Multi-Format Import, Export & Local Editing
- **Comprehensive Format Support:** Full parser and generator support for **LRC**, **TTML / XML**, **SRT**, **VTT**, **TXT**, **JSON**, and **QRC** (`QrcParser.kt`).
- **Persistent Local Lyrics Storage:** Built `CustomLyricsStorage.kt` enabling users to save, modify, or override lyrics per track locally with automatic backup.
- **Multi-Provider Aggregation:** Seamless integration across Kouzu MLS, BetterLyrics Portato, Bini, Unison, Musixmatch Unified, and Paxsenix.

### Advanced Multi-Lingual Romanization
- **Japanese Kuromoji Analyzer:** Integrated Kuromoji IPADIC morphological analyzer for authentic Kanji-to-Romaji transliteration (`LyricsRomanizer.kt`).
- **Pinyin & Hangul Transliteration:** Automated Pinyin (Mandarin) and Revised Romanization (Korean Hangul) with word-by-word timestamp alignment.

### Lyric Share Studio & W3C High-Contrast Cards
- **Dynamic W3C Contrast Calculation:** Automated background luminance analysis in `LyricsImageCard.kt` and `ComposeToImage.kt`, guaranteeing readable text across light and dark artwork.
- **Social Story Formats:** 9:16 vertical and square card generation with custom typography and vibrant glass backgrounds for Instagram and messaging apps.

---

## 🎨 3. Visual Design, Liquid Glass & M3 Expressive UI

### Liquid Glass & Real-Time Blur System
- **Kyant Backdrop & Chris Banes Haze:** Integrated `com.kyant.backdrop` and `dev.chrisbanes.haze` for real-time translucent optical refraction.
- **SpatialFloatingNavBar.kt:** Introduced Apple Music-style interactive floating navigation bar featuring sliding active pill motion and press-to-scale touch response via `layerBlock`.
- **Header Liquid Glass Action Pills:** Added glass back buttons and header action pills with native blur.
- **Graceful Solid Fallback Engine:** Automatic fallbacks to solid tonal surfaces on low-spec hardware or when liquid glass is disabled by the user.
- **All-in-One Liquid Glass Master Toggle:** Added centralized master toggle in Appearance Settings to enable or disable glass optics across all UI surfaces simultaneously.

### Material 3 Expressive ButtonGroups & Sliders
- **Pushing Neighbor Morphing:** Integrated M3 Expressive button groups with pushing neighbor animation (`expandedRatio = 0.25f - 0.3f`), spring shape morphing, and `Spring.StiffnessLow` physics in `PlayerTransportControls.kt`.
- **Connected Action Buttons:** Redesigned Artist and Album action headers with unified `Play` and `Radio` connected pill buttons.
- **Expressive Music Sliders:** Added `SpatialWaveMusicSlider.kt` and `AppleMusicSlider.kt` with live haptic scrubbing and fluid touch expansion.

### Playful Collage & Apple Music Typography
- **Playful Collage Theme:** Introduced expressive Android theme option in `PlayerThemeBottomSheet.kt` with dynamic organic shapes and pastel tones.
- **Apple Music Typography:** Implemented Google Sans Flex variable axis font tokens (`Type.kt`) with calibrated letter tracking and optical weight scaling.

### Adaptive Layout & System Navigation
- **Foldable, Tablet & Landscape Adaptation:** Polished `FullPlayer.kt` and detail pages to dynamically adapt to varying aspect ratios, multi-window splits, and landscape mode.
- **Responsive Tablet & Big-Screen Explore Banners:** Redesigned `ExploreHome.kt` and `CategoryCard.kt` with responsive constraint sizing and aspect ratio bounding, preventing Spotify banners from stretching or distorting on wide screens.
- **Top Rounded Corner Navigation Bar:** Elevated navigation bar with top-only rounded corners and proper window insets.
- **Predictive Back Gestures:** Full support for Android 14/15/16 predictive back animations in Navigation Compose via `MotionScheme.expressive()`.
- **Dynamic App Launcher Icons:** Added dynamic launcher icon switcher (`AppIconManager.kt`) utilizing `<activity-alias>` configurations (Filled Dark, Filled Light, Stroked Dark, Stroked Light, No-BG).

---

## 📡 4. SpatialFlow Live: Real-Time Listener Presence

### Cloudflare Worker Architecture & Pulse Synchronization
- **Live Presence Engine (`SpatialFlowLiveManager.kt`):** Created lightweight, privacy-focused listener presence engine connected to secure Cloudflare Workers infrastructure.
- **Battery-Conscious Heartbeat:** Dispatches heartbeat pulses strictly during active playback intervals, immediately sleeping when playback pauses or stops.
- **Real-Time Listener Count:** Queries live aggregated listener counts across the SpatialFlow community for the currently active track.

### Material 3 Expressive UI & User Privacy
- **SpatialFlow Live Bottom Sheet (`SpatialFlowLiveBottomSheet.kt`):** Built expressive bottom sheet presenting active listener counts, current track status, and connection diagnostics.
- **Icon-Only Clean Design:** Adheres strictly to clean vector iconography, eliminating emojis, colored status dots, or visual clutter.
- **Privacy Controls (`SettingsPrivacySection.kt`):** Dedicated toggle in Settings allowing users to easily opt in or out of presence broadcasting at any time.
- **Security & Secret Protection:** Secured all worker API requests with signed request headers and token encapsulation, ensuring zero endpoint leaks in source control or build logs.

---

## 🎬 5. Canvas Video Artwork & Dynamic Transitions

### High-Performance Canvas Engine
- **36× Downscale-Blur-Upscale Pipeline:** Ported high-efficiency canvas video background renderer (`CanvasArtworkPlayer.kt`, `AppleMusicBackground.kt`) delivering buttery 60/120 FPS playback.
- **Shared LRU Video Cache:** Implemented `CacheDataSource` and lightweight `CanvasLoadControl` to prevent memory thrashing and network re-fetching.
- **Hardware Decoder Capping:** Restricted off-screen canvas playback to hardware video decoders with resolution capping to safeguard battery life and thermal headroom.
- **Real-Time Palette Synchronization:** Synchronized background ambient gradients and light meshes with Canvas video frame colors in real-time (`PlayerPaletteEffect.kt`).

### 4 Authentic Album Art Transitions
- **Shuffle, Push, Flip & Crossfade:** Recreated all 4 signature album art transitions with 100% visual parity:
  - `ShuffleArtworkTransition.kt`
  - `PushArtworkTransition.kt`
  - `FlipArtworkTransition.kt`
  - `CrossfadeArtworkTransition.kt`
- **Instant Gesture Reactivity:** Fixed artwork swipe gesture toggling in `ArtworkPager.kt` so user preferences apply immediately without app restart.

---

## 📊 6. YouTube Music 2025 Recap: Story Experience

### Complete Architectural & Visual Redesign
- **Cinematic Story Experience:** Upgraded listening recap into an authentic YouTube Music 2024/2025 story deck with gesture-driven progress bars (`RecapStoryDialog.kt`, `RecapPosterCard.kt`).
- **Dynamic Palette Harmonization:** Integrated AndroidX Palette extraction for #1 top song and #1 top artist photos, with grayscale hue corrections in `RecapThemeEngine.kt`.
- **Database Schema Migration (Room 5 $\to$ 6):** Optimized database queries and indices in `MusicDatabase.kt` and `HistoryEventEntity.kt`, eliminating 2–4 second UI freezes.
- **Eliminated ANR & Deadlocks:** Cleared SurfaceSyncGroup synchronization deadlocks and JPEG decode storms when opening the Recap tab.
- **High-Fidelity Recap Sharing:** Added `RecapShareHelper.kt` and `RecapShareSheet.kt` to export poster images directly to Instagram Stories, WhatsApp, and social platforms.

---

## 🌐 7. Integrations, Scrobbling & Cloud Sync

### Last.fm Native Connector
- **Background Scrobbler:** Added native scrobbling with play percentage threshold detection.
- **Offline Scrobble Queue:** Offline Room database caching (`com.codetrio.spatialflow.data.lastfm`) that automatically flushes cached plays when internet connectivity resumes.
- **Real-Time "Now Playing" Updates:** Real-time track status synchronization to Last.fm profile.
- **Loved Tracks Sync:** 1-tap track favoriting directly synced with Last.fm Loved Tracks.
- **Dedicated Authentication Screen:** In-app web and credentials login flow (`LastFmSignInScreen.kt`, `SettingsLastFmSection.kt`).

### Spotify Library & Canvas Integration
- **In-App WebView Authentication:** Seamless Spotify login capturing `sp_dc` cookie session securely (`SpotifySignInScreen.kt`).
- **Manual Token Input Alternative:** Added `SpotifyManualTokenBottomSheet.kt` for manual session token entry.
- **Spotify Explore Feed:** Curated Spotify playlists feed with parallax header design matching YouTube Music detail views.
- **Canvas Video Fetching:** Direct retrieval of official Spotify Canvas looping videos via `SpotifyCanvasProvider.kt`.

### InnerTube 2026 Engine & YouTube Management
- **Updated InnerTube Client Matrix:** Upgraded to 2026 InnerTube client configurations with bypass for restricted streams.
- **Manual YouTube Cookie Manager:** Added `YouTubeManualCookieBottomSheet.kt` for manual session and age-verification cookies.
- **Floating Connection Banner:** Added YouTube-style offline/online status indicator (`YoutubeConnectionBanner.kt`) with navigation lift animations.
- **Full Playlist Bulk Download:** Enabled multi-track selection and batch downloading directly from playlists with format preferences.

### In-App GitHub Update Manager
- **WorkManager Update Worker (`UpdateCheckWorker.kt`):** Background scheduled worker periodically checks GitHub releases for newer APK distributions.
- **Update Bottom Sheet (`UpdateBottomSheet.kt`):** Presents rich changelogs, release tags, and direct APK download / installation prompts.
- **Broadcast Receiver Updates (`UpdateReceiver.kt`):** Manages seamless APK download and package installer intents.

---

## ⚙️ 8. System Hardware, Haptics & Accessibility

### Dual-Layer Precision Haptics Engine
- **Universal Format Haptics:** Re-architected `PlayerHapticManager.kt` to synchronize haptics across **FLAC**, **OPUS**, **MP3**, and **AAC** tracks.
- **Zero-Jitter Transient Dispatch:** Eliminated competing motor lockouts and asynchronous thread hops (15–35ms dispatch jitter) for crisp tactile attacks.
- **Sub-Bass & Frequency Humming:** Tuned vibration motor resonance to mirror Apple Music's bass haptic rhythm.
- **Device Capabilities Audit:** Added detailed hardware feedback diagnostics in `SettingsHapticsSection.kt` displaying supported vibrator features (amplitude control, envelope effects).

### Audio Output Manager & Device Switcher
- **In-App Device Switcher:** Added `AudioOutputManager.kt` and `AudioOutputIndicator.kt` to display and switch between Bluetooth headsets, wired headphones, phone speakers, and USB DACs directly from the player.

### Earbud "Tap to Action" Custom Gesture Mapping
- **Custom Hardware Mapping:** Added earbud gesture interceptor allowing single/double/triple taps to trigger actions such as "Add to Queue & Download".

---

## 🏗️ 9. Codebase Modularization, Clean-Up & Contributors

### Monolithic File Refactoring
- **FullPlayer.kt Modularization (Reduced by 60%+):** Extracted isolated composables into `com.codetrio.spatialflow.ui.player.*`:
  - `PlayerHeaderBar.kt`
  - `PlayerTransportControls.kt`
  - `PlayerMetadataChips.kt`
  - `PlayerOptionsBottomSheet.kt`
  - `AppleMusicSlider.kt`
  - `ClickableArtists.kt`
  - `FullPlayerCompositionPolicy.kt`
  - `FullPlayerVisualState.kt`
  - `FullPlayerRuntimePolicy.kt`
- **SettingsFragment.kt Modularization:** Partitioned massive settings screen into clean domain modules under `com.codetrio.spatialflow.ui.settings.*`:
  - `SettingsAppearanceSection.kt`
  - `SettingsAudioSection.kt`
  - `SettingsPlaybackSection.kt`
  - `SettingsHapticsSection.kt`
  - `SettingsLastFmSection.kt`
  - `SettingsMusicManagementSection.kt`
  - `SettingsStorageSection.kt`
  - `SettingsCustomizationSection.kt`
  - `SettingsAboutSection.kt`
  - `SettingsWhatsNewSection.kt`

### Contributors Showcase
- **Segmented Contributors List:** Added prominent Contributors section in `SettingsAboutSection.kt` recognizing community contributors:
  - [4nx3b (yuki)](https://github.com/4nx3b)
  - [vossgraves](https://github.com/vossgraves)
- **Profile Rendering:** Uses `SpatialImage` and `SegmentedList` to render avatars and clickable profile links.

### Comprehensive Codebase Cleanup
- **Purged Dead Code & Tests:** Deleted obsolete dummy tests (`ExampleUnitTest.java`, `InnerTubeSearchTest.kt`, `BitPerfectPipelineTest.kt`) and legacy splash layouts (`activity_splash.xml`).
- **Removed Deprecated App Names:** Scoured codebase to remove legacy project names, ensuring consistent SpatialFlow branding.
- **Git Repository Hygiene:** Permanently untracked and removed `.idea/` cache directories from git, preventing accidental leakage of IDE configs or local properties.
- **Updated Copyright Year:** Advanced copyright notice to `© 2026 Shubham Karande` in `SettingsFragment.kt`.

### Compose Recomposition & Memory Optimization
- **Zero-Recomposition Scroll Pass:** Added stable state contracts, `@Stable` / `@Immutable` annotations, and explicit item keys (`key` + `contentType`) across all LazyColumns and LazyRows.
- **Decoupled Sheet Transition Progress:** Replaced competing animatables with a single authoritative progress driver (`sheetProgress: Animatable<Float, AnimationVector1D>`) in `PlayerBottomSheetCompose.kt`.
- **Zero-Copy GPU Bitmaps:** Enabled `allowHardware(true)` and dynamic CDN downscaling (`String.resizeCDN`) for album art and avatars, slashing heap allocation.
- **R8 / ProGuard Rules Preservation:** Configured explicit keep rules in `proguard-rules.pro` for `com.kyant.backdrop.**` and `dev.chrisbanes.haze.**` to prevent release build minification crashes.

---

## 🐛 10. Bug Fixes & Stability Improvements

| Issue / Symptom | Root Cause | Fix Applied in v1.8.2 |
| :--- | :--- | :--- |
| **Playback Bug:** Playback stops unexpectedly in background | OEM battery killer killing playback threads; unhandled Wi-Fi power save | Acquired `PARTIAL_WAKE_LOCK` and `WIFI_MODE_FULL_HIGH_PERF` in `AudioPlaybackService.kt` |
| **Lyrics Bug:** Background vocals freezing mid-word | Character offset subtraction misaligned syllable ranges | Replaced with token-based syllable mapper & proportional duration distribution in `KaraokeLyricsView.kt` |
| **Lyrics Bug:** Raw `<tt>` / XML markup rendered on screen | TTML parser failure falling through to LRC parser; unescaped `&` in XML | Added XML pre-sanitization, regex fallback parser, and suppressed XML fallthrough in `LrcParser.kt` |
| **Lyrics Bug:** Background vocals missing exit animation | `isBracketVisibleState` only checked start time, staying `true` indefinitely | Added `pos >= (bgEndMs + 350L)` exit condition, triggering M3 spring shrink & fade-out |
| **Lyrics Bug:** Background vocals already visible on upcoming lines | Unconditional rendering of bracket content on all lines | Gated visibility to `pos >= (bgStartMs - 350L)` with full reading mode on manual scroll |
| **Library Bug:** Local songs all showing same album cover | MediaStore album ID collision for untagged tracks sharing generic album IDs | Created `AudioArtworkFetcher` to extract embedded art directly per audio track |
| **UI Bug:** Spotify Explore banner broken on big screens | Fixed width calculations causing distortion on wide tablet displays | Added responsive constraint sizing and aspect ratio bounding in `ExploreHome.kt` |
| **System:** Stale copyright year in Settings | Copyright notice hardcoded to 2025 | Updated copyright notice to `© 2026 Shubham Karande` in `SettingsFragment.kt` |
| **Packaging:** Fastlane metadata out of sync | Fastlane changelogs missing v1.8.2 release definition | Added v1.8.2 (versionCode 10) release notes to Fastlane metadata |
| **Crash:** `IndexOutOfBoundsException` in `addToQueueNext` | Queue index calculated outside valid bounds on empty or rapidly shifting playlists | Added `.coerceIn(0, currentQueue.size)` constraint validation |
| **Crash:** Compose `maxWidth >= minWidth` | Measure policy violation during rapid split-screen or orientation changes | Wrapped layouts in safe constraint bounds and isolated measurement phases |
| **Crash:** Duplicate `LazyRow` / `LazyColumn` key collision | Duplicate track IDs present in same playlist/album | Appended stable unique index suffixes (`"$id-$index"`) to lazy list keys |
| **Crash:** Track deletion exception in `SongDownloader` | File delete operation running on main thread while media scanner active | Dispatched track deletion to `Dispatchers.IO` with verified scanner callback |
| **Crash:** SurfaceSyncGroup ANR deadlock in Recap | Synchronous texture generation contending with Compose draw phase | Offloaded bitmap generation to background coroutines with cached surface handles |
| **Crash / Bug:** Infinite runaway track skipping | Unhandled extractor failure triggering immediate consecutive skips | Implemented `SkipDebouncer.kt` and `QueuePreloader.kt` to throttle failures |
| **Audio Bug:** HTTP 403 Forbidden playback errors | Expired Google Video playback tokens and strict user agent verification | Implemented dynamic header forging in `StreamClientUtils.kt` |
| **Audio Bug:** No sound on MP3/AAC or no haptics on FLAC/OPUS | AudioTrack buffer format mismatch between PCM and direct bitstream modes | Unified decoder pipeline with synchronized buffer timestamps |
| **Audio Bug:** Studio Sound failing to re-apply after track skip | Singleton race condition between dual crossfade ExoPlayer instances | Replaced shared singleton with instance-bound DSP configuration |
| **Audio Bug:** Tidal 96 kHz hardcoded limit | Hardcoded sample rate cap ignoring incoming stream headers | Implemented dynamic sample rate negotiation up to 192 kHz |
| **UI Bug:** Player collapse miscalculation when nav bar hidden | Sheet offset calculation using stale absolute screen coordinates | Factored dynamic window insets into translation progress |
| **UI Bug:** Duplicate app launcher icon in launcher | Multiple launcher intent filters declared across activity aliases in Manifest | Cleaned `<intent-filter>` declarations in `AndroidManifest.xml` |
| **UI Bug:** Unreadable white text on exported lyric cards | Static white text overlaying light or desaturated artwork | Implemented dynamic W3C WCAG luminance check in `LyricsImageCard.kt` |
| **UI Bug:** Grayscale album art forcing red saturation in Recap | Zero saturation defaulting hue calculation to 0° (pure red) | Added achromatic greyscale detection fallback in `RecapThemeEngine.kt` |
| **UI Bug:** Artwork swipe gesture toggle non-reactive | State update missing Compose recomposition trigger | Bound toggle directly to mutable snapshot state in `ArtworkPager.kt` |
| **UI Bug:** Release APK crashes with Backdrop / Haze | R8 bytecode optimizer stripping reflection calls on blurred surfaces | Added ProGuard keep rules for Kyant Backdrop and Chris Banes Haze |

---

## 📦 11. Dependency, Packaging & Distribution Updates

### Build Configuration & SDK Alignment
- **Target SDK:** Android 16 (API 36) | **Compile SDK:** API 37
- **Gradle Version:** `8.13` | **Android Gradle Plugin (AGP):** `8.9.0`
- **Gradle Version Plugin:** Updated to `io.github.ben-manes.versions:0.64.0`
- **NDK Version:** `30.0.15729638`
- **Full Release Verification:** Verified R8 code minification, resource shrinking, and native C++ builds across all supported ABIs:
  - `app-universal-release-unsigned.apk` (101.07 MB)
  - `app-arm64-v8a-release-unsigned.apk` (43.56 MB)
  - `app-armeabi-v7a-release-unsigned.apk` (60.27 MB)
  - `app-x86-release-unsigned.apk` (43.80 MB)
  - `app-x86_64-release-unsigned.apk` (48.25 MB)

### Fastlane Store Metadata & Release Packaging
- **Fastlane Metadata:** Configured comprehensive app store metadata under `fastlane/metadata/android/en-US/`:
  - `title.txt`: SpatialFlow
  - `short_description.txt`: High-fidelity music player with spatial audio, real-time lyrics & streaming.
  - `full_description.txt`: In-depth description detailing audiophile features, lyrics engine, and privacy-first design.
  - `changelogs/10.txt`: Detailed release notes for v1.8.2.
  - Screenshots & Graphics: 8 high-resolution phone screenshots (`1.png` - `8.png`), `icon.png`, and `featureGraphic.png`.

### New Libraries Added
- `dev.chrisbanes.haze:haze-core` & `haze-materials` (Real-Time Dynamic Blur)
- `com.kyant.backdrop:backdrop` & `com.kyant.shapes:shapes` (Liquid Glass & Continuous Corner Shapes)
- `com.atilika.kuromoji:kuromoji-ipadic` (Japanese Morphological Romaji Analyzer)
- `androidx.work:work-runtime-ktx` (Background Update & Task Scheduling)
- `androidx.security:security-crypto` (Keystore Encrypted Preferences)
- `androidx.webkit:webkit` (Secure WebView Authentication & Cookie Storage)
- `com.google.zxing:core` (QR Code Generation for Supporter & Profile Sharing)
- `org.mozilla:rhino` (JavaScript Engine for Stream Extraction)

---

## 📝 12. Upgrading from v1.8.1

- **Backward Compatibility:** All existing downloaded tracks, local playlists, history, and settings from v1.8.1 are automatically preserved and migrated.
- **Database Migration:** Room Database automatically runs migration `5 -> 6` on first launch without user intervention.
- **Cache Recommendation:** If you experience any visual blur or artwork artifacts after upgrading, clear the app image cache once in **Settings $\to$ Storage $\to$ Clear Image Cache**.
