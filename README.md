# YouTube Downloader (Android APK)

Android application built with Kotlin, Jetpack Compose (Material 3), and `youtubedl-android` (`yt-dlp` + `FFmpeg`) to download YouTube videos as **MP4** or extract audio as **MP3**.

## Features
- **MP4 Video Download**: Selectable quality (`Best`, `1080p`, `720p`, `480p`, `360p`).
- **MP3 Audio Extraction**: Selectable bitrate (`320 kbps`, `192 kbps`, `128 kbps`).
- **Share Sheet Integration**: Share links directly from the YouTube app (`ACTION_SEND`).
- **Real-Time Progress & Cancellation**: Live download percentage, ETA, and cancel support.
- **On-Device `yt-dlp` Update**: Update the extractor engine from the top app bar without rebuilding the APK.
- **Scoped Storage Ready**: Saves output to `Downloads/YouTubeDownload/` via `MediaStore` on Android 10+ (API 29+) and public Downloads on Android 7–9.

## Build APK

### Option 1: Android Studio (Local)
1. Open this folder in **Android Studio** (Ladybug or newer, JDK 17, Android SDK 35).
2. Let Gradle sync dependencies.
3. Run **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. Output APK: `app/build/outputs/apk/debug/app-debug.apk`.

### Option 2: GitHub Actions (No Local Android SDK Required)
Push this repository to GitHub; the included workflow [build-apk.yml](.github/workflows/build-apk.yml) runs `gradle testDebugUnitTest` and `gradle assembleDebug`, and uploads `app-debug.apk` as a workflow artifact.
