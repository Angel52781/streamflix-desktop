# Port status

Upstream reference reviewed: `streamflix-reborn2/streamflix` at commit `91b27174c8864b5365c0966fd18221b428c2700a` (`Bump version to 1.7.231`, 2026-09-07).

## Compatibility in Windows release 1.0

| Area | Status | Notes |
|---|---|---|
| Windows desktop UI | Implemented | Swing/JVM, mouse + keyboard friendly |
| Android SDK dependency | Removed from MVP | Java 17+ only |
| Core features | Ported | Movies, Series, Search, Seasons/episodes, Server discovery |
| Direct HLS/MP4 | Implemented | mpv |
| Providers / Extractors | Branch dependent | Subject to change based on active branch |
| Cloudflare/WebView bypass | Pending | Needs WebView2/JCEF strategy |
| Profiles/Supabase | Pending | Not part of MVP |
| History/favorites | Pending | Not part of MVP |
| Chromecast | Pending | Not part of MVP |

## Validation performed

Validated in the available Linux build environment:

- `javac --release 21` compiles all production sources.
- Runnable JAR is produced with `dev.streamflix.desktop.App` as main class.
- JSON parser test passes.
- FanPelis response-mapping fixture test passes.
- Extractor fixture/crypto test passes.
- `jpackage --type app-image` successfully produces a desktop application image with bundled runtime on Linux, validating the packaging structure.

Not validated here:

- Live FanPelis requests (build sandbox has no outbound network).
- Live Filemoon/VOE/Streamtape extraction.
- Windows `jpackage` output itself (must execute the included `build-windows.bat` on Windows/JDK 21+).
- Actual mpv playback on Windows.

## Windows validation 2026-09-15

- Packaged StreamflixDesktop.exe produced successfully.
- Packaged self-test: SELF_TEST_OK.
- Live movie playback path validated through mpv.
- Live series episode playback path validated through mpv.
- Bundled Java runtime and bundled mpv verified in the packaged app image.
- Normal GUI launch remained running after startup smoke test.
