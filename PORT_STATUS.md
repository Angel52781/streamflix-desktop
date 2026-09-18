# Port status

Upstream reference reviewed: `streamflix-reborn2/streamflix` at commit `91b27174c8864b5365c0966fd18221b428c2700a` (2026-09-07).

## Current Windows state

Development target: **1.3.5**. Public GitHub release at the time of this document: **1.2.0**.

| Area | Status | Notes |
|---|---|---|
| Windows desktop UI | Implemented | Swing/JVM, mouse + keyboard, HiDPI-aware |
| Movies / Series / Search | Implemented | TMDb EN/ES plus alternative providers |
| Catalog filters | Implemented | Popular and genre filters with progressive loading |
| Home rails | Implemented | Contextual horizontal wheel, arrows, Ver más |
| Seasons / episodes | Implemented | Includes specials and progress-aware episode UI |
| History / favorites | Implemented | Persistent local data with atomic writes |
| Continue watching | Implemented | Movie and episode resume |
| Direct HLS/MP4 playback | Implemented | Bundled mpv + JSON IPC |
| Playback fallback | Implemented | Startup-aware fallback and local server ranking |
| Playback recovery | Implemented | Stall detection, bitrate downgrade and resume |
| IPTV / Pluto | Implemented | Spain / world IPTV and Pluto MX/ES/US |
| In-app updates | Development 1.3.5 | GitHub Releases + SHA-256 + rollback-capable portable updater |
| Persistent image cache | Development 1.3.5 | %LOCALAPPDATA%, bounded and expiring |
| Diagnostics / logs | Development 1.3.5 | Local rotating log and privacy-filtered diagnostic ZIP |
| Chromecast | Not implemented | Windows parity remains future work |
| Profiles / Supabase | Out of scope | No Streamflix login planned for desktop |
## Validation

Validated on Windows:

- Java sources compile with `javac --release 17`.
- Deterministic tests run on every local build.
- `jpackage --type app-image` produces `StreamflixDesktop.exe` with bundled Java runtime.
- Packaged `--self-test` verifies runtime dependencies, provider registry, version consistency, mpv and updater write access.
- Real movie and series playback paths have been validated through mpv.
- The 1.3.5 image cache was observed writing real catalog images under `%LOCALAPPDATA%\Streamflix\cache\images`.
- `release.ps1` creates versioned and stable-name ZIP/SHA-256 assets.
- The pinned mpv archive SHA-256 was independently re-downloaded and matched before being committed to the setup script.

Deterministic gates currently include JSON, provider/TMDb/M3U fixtures, extractors, dependency smoke,
user data, mpv player, playback fallback/server ranking, updater parsing, image disk cache and diagnostics privacy tests.

## Release engineering

- `.github/workflows/ci.yml` runs deterministic builds/tests on Windows and Linux.
- `.github/workflows/release.yml` is tag-driven and refuses a tag that does not match `VERSION`.
- Release jobs download the pinned mpv archive, run the full packaged self-test, and publish both stable-name and versioned assets.
- The application checks GitHub Releases asynchronously; no Streamflix backend is required.
- User settings, favorites and history live outside the application directory and are not replaced by updates.

## Remaining audit items

- The portable updater still needs one real public old-version -> new-version end-to-end update after a release newer than the installed build exists.
- TMDb playback currently has one certified route (VixSrc); independent playback routes would improve resilience.
- Some public provider sites can change HTML, domain or anti-bot behavior without notice.
- `MainFrame` and `EmbeddedPlayerWindow` remain large classes and should be decomposed incrementally, not rewritten wholesale.
- More silent provider/extractor fallback catches should be converted to structured diagnostics where doing so does not create noisy logs.
- Public redistribution of the bundled third-party mpv binary requires a dedicated licensing/compliance pass before the next release.
