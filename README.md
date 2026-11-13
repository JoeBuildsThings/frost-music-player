<div align="center">

# Frost Music Player

**Lightweight, Offline-First Native Android Music Player Built with Kotlin, Jetpack Compose & Media3**

<p>
  <a href="https://github.com/JoeBuildsThings/frost-music-player/actions/workflows/build.yml"><img src="https://github.com/JoeBuildsThings/frost-music-player/actions/workflows/build.yml/badge.svg" alt="Build Frost APK" /></a>
  <a href="https://github.com/JoeBuildsThings/frost-music-player/releases/latest"><img src="https://img.shields.io/badge/Download-Latest%20APK-56d364?style=flat-square&logo=android&logoColor=0b0f14" alt="Download Latest APK" /></a>
  <img src="https://img.shields.io/badge/Android-6.0%2B%20(API%2023%E2%80%9334)-0b0f14?style=flat-square&logo=android&logoColor=3DDC84" alt="Android 6.0+" />
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-0b0f14?style=flat-square&logo=kotlin&logoColor=7F52FF" alt="Kotlin & Jetpack Compose" />
  <img src="https://img.shields.io/badge/Engine-AndroidX%20Media3%201.8.0-0b0f14?style=flat-square&logo=googleplay&logoColor=f0f3f6" alt="Media3 ExoPlayer" />
</p>

**[Download the Latest Release APK](https://github.com/JoeBuildsThings/frost-music-player/releases/latest)**

</div>

---

### Overview

Frost Music Player is a native Android local audio player engineered for fast cold starts, low memory overhead, and uninterrupted background playback. Built with **Kotlin**, **Jetpack Compose**, and **AndroidX Media3 (`ExoPlayer`)**, it indexes device audio directly from Android's `MediaStore` with atomic disk caching so large libraries load immediately without network dependencies, accounts, or analytics.

R8 code minification and resource shrinking keep the release APK compact (~2 MB) while supporting Android 6.0 (`minSdk 23`) through Android 14 (`targetSdk 34`).

---

### Download & Install

1. Open the **[Releases page](https://github.com/JoeBuildsThings/frost-music-player/releases/latest)** and download the latest `Frost-Music-Player-*.apk` asset.
2. When prompted by Android, allow installing apps from your browser or file manager.
3. Launch **Frost Music Player** and grant audio storage permission (`READ_MEDIA_AUDIO` on Android 13+, `READ_EXTERNAL_STORAGE` on Android 6–12) to index your local tracks.

> **CI Artifacts:** Every push to `main` also compiles debug and R8-minified release APKs in **[GitHub Actions](https://github.com/JoeBuildsThings/frost-music-player/actions/workflows/build.yml)**.

---

### Core Capabilities

* **Instant Library Hydration:** Loads cached track metadata from atomic local JSON storage (`LibraryCache`) on launch while syncing changes in the background via a `MediaStore` `ContentObserver`.
* **Five-Way Library Navigation:** Browse local audio by **Songs**, **Albums**, **Artists**, **Folders**, and **Playlists**, plus instant search, custom sort modes, folder exclusions, and configurable minimum track duration filtering.
* **Background Playback & MediaSession:** Runs a foreground `MediaSessionService` (`PlaybackService`) with `WAKE_MODE_LOCAL`, automatic pause on headphone/Bluetooth disconnect (`setHandleAudioBecomingNoisy`), configurable audio focus behavior, and lock-screen/notification transport controls.
* **Hardware DSP Audio Effects:** Attaches Android's native multi-band `Equalizer` and `BassBoost` directly to the active ExoPlayer audio session ID with persistent user presets.
* **Playback Controls & Sleep Timer:** Variable playback speed cycling, shuffle, repeat-off / repeat-all / repeat-one, interactive queue jumping, smart previous-track restart (restarts track after 3 seconds of playback), and a countdown sleep timer.
* **Home-Screen Widget:** Lightweight `AppWidgetProvider` (`FrostWidgetProvider`) with play/pause, next, and previous controls synchronized via `MediaController` IPC.
* **Zero Telemetry:** No internet permission, no accounts, no ads, and no tracking. Library caches, playlists, favorites, play counts, and playback positions remain strictly on-device.

---

### System Architecture

```text
+-----------------------------------------------------------------------+
|                         Jetpack Compose UI Layer                      |
|  FrostApp  |  NowPlayingScreen  |  SearchScreen  |  SettingsScreen    |
|  Pages: Songs · Albums · Artists · Folders · Playlists                |
+-----------------------------------+-----------------------------------+
                                    | Unidirectional State & Events
                                    v
+-----------------------------------------------------------------------+
|                       FrostViewModel (AndroidViewModel)               |
|  · Serialized I/O Dispatcher (ioSerial) & MediaStore ContentObserver  |
|  · Queue, Shuffle/Repeat, Speed, Sleep Timer & Play-Count State       |
+---------------+---------------------------------------+---------------+
                | MediaController / SessionToken        | Persistence
                v                                       v
+-------------------------------+       +-------------------------------+
|  PlaybackService              |       |  Local Storage & Cache Layer  |
|  (MediaSessionService)        |       |  · MediaStoreRepository       |
|  · ExoPlayer 1.8.0            |       |  · LibraryCache (atomic JSON) |
|  · Equalizer & BassBoost DSP  |       |  · UserStore & SessionStore   |
|  · FrostWidgetProvider Sync   |       |  · ArtCache (12MB LruCache)   |
+-------------------------------+       +-------------------------------+
```

#### Package Structure (`com.joebuildsthings.frost`)

| Module / File | Responsibility |
| :--- | :--- |
| `FrostViewModel.kt` | Central state holder coordinating library scans, queue mutations, sleep timer coroutines, and `MediaController` commands |
| `playback/PlaybackService.kt` | Foreground `MediaSessionService` managing `ExoPlayer`, audio attributes/focus, `Equalizer` + `BassBoost` DSP attachment, and widget state broadcasts |
| `data/MediaStoreRepository.kt` | Queries `MediaStore.Audio.Media` with duration thresholds and excluded-directory filtering |
| `data/LibraryCache.kt` | Write-to-temp-and-rename (`frost_library.json.tmp`) cache for instant cold-start library rendering |
| `data/UserStore.kt` & `SessionStore.kt` | Atomic JSON persistence for playlists, favorites, play counts, recent history, and active queue/position restoration |
| `data/ArtCache.kt` | Thread-safe 12 MB `LruCache` with negative-lookup tracking; uses `ContentResolver.loadThumbnail` on API 29+ and power-of-two `BitmapFactory` subsampling on API 23–28 |
| `widget/FrostWidgetProvider.kt` | `AppWidgetProvider` bridging home-screen broadcast intents to `PlaybackService` via `MediaController` |
| `ui/` & `ui/theme/` | Jetpack Compose screens, frosted-glass panels (`dev.chrisbanes.haze`), and dynamic album-art color extraction (`ArtPalette.kt`) |

---

### Build from Source

**Requirements:** JDK 17, Gradle 8.13+, and Android SDK (`compileSdk 36`).

```bash
git clone https://github.com/JoeBuildsThings/frost-music-player.git
cd frost-music-player

# Build R8-minified release APK
./gradlew assembleRelease --no-daemon

# Or build debug APK
./gradlew assembleDebug --no-daemon
```

Output paths:
* Release APK: `app/build/outputs/apk/release/app-release.apk`
* Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

---

### Author

Built by **Adebiyi Joseph Ayomide** ([@JoeBuildsThings](https://github.com/JoeBuildsThings)).
