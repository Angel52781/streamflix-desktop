# Streamflix Desktop for Windows

A Windows-oriented desktop port of the Streamflix Reborn architecture. The goal is functional parity with the Android app while replacing Android-only UI/player components with desktop equivalents.

## Current release

Implemented:

- Windows/desktop Swing UI (no Android SDK required)
- Movies and TV-series browsing through the FanPelis provider API
- Search
- Series seasons/episodes
- Server discovery
- Direct HLS/MP4 playback through bundled mpv
- Native extraction for Filemoon, VOE, Streamtape, DoodStream and Goodstream
- HTTP headers/cookies passed to mpv when required
- Automatic server fallback plus browser fallback for unsupported hosts
- Java 17-compatible build with a bundled runtime in the Windows app image
- Windows `jpackage` app-image build script

Not yet at Android parity:

- Only the first provider (`FanPelis`) is wired into the desktop UI
- Only direct streams + Filemoon have native extraction in this MVP
- Other Streamflix providers/extractors still need systematic JVM ports
- Android WebView/Cloudflare bypass flows still need a WebView2/JCEF desktop replacement
- Supabase profile sync, favorites/history sync and Chromecast are not yet ported

## Requirements on Windows

1. JDK 21 or newer (`java`, `javac`, `jar`, `jpackage` on PATH)
2. [mpv](https://mpv.io/) for in-app playback

The launcher searches for mpv in this order:

1. `STREAMFLIX_MPV` environment variable
2. `tools\mpv\mpv.exe` inside this project
3. `mpv.exe` on PATH
4. common Windows installation paths

## Run from source on Windows

```bat
run-windows.bat
```

## Build a Windows app image

```bat
build-windows.bat
```

Output:

```text
dist\StreamflixDesktop\StreamflixDesktop.exe
```

`jpackage --type app-image` does not require an MSI installer toolchain and bundles a Java runtime with the app image.

## Linux/macOS development build

```bash
./build.sh
java -jar build/streamflix-desktop.jar
```

## Architecture

```text
Desktop UI (Swing)
      |
      v
Provider interface
      |
      +-- FanpelisProvider
      |
      v
Server / embed URL
      |
      v
ExtractorRegistry
      |-- Direct stream
      |-- Filemoon
      `-- Browser fallback
      |
      v
mpv
```

The important design decision is that providers/extractors are desktop-JVM code, independent of Android. Additional Streamflix providers can therefore be moved over incrementally instead of rewriting the application again.

## Legal

This software does not host media. It is provided for educational/personal use and as an interoperability experiment. Ensure you have the right to access any content you request through third-party providers.
