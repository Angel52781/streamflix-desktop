# Streamflix Desktop for Windows

Streamflix Desktop is a Windows/JVM port of the Streamflix Reborn provider architecture. It keeps the provider/extractor model, replaces Android-only UI and player components with desktop implementations, and packages as a native Windows app image.

## Download for Windows

**[Download the latest Streamflix Desktop build](https://github.com/Angel52781/streamflix-desktop/releases/latest/download/StreamflixDesktop-windows.zip)**

Extract the ZIP and run `StreamflixDesktop.exe`. The portable package already includes the required Java runtime and mpv.

You can also browse versioned releases and checksums on the [GitHub Releases page](https://github.com/Angel52781/streamflix-desktop/releases).

## Current stable release

Version: **1.2.0**

The `main` branch is currently preparing **1.3.4**, focused on smoother streaming-style rail navigation, clearer catalog filters and TMDb onboarding.

### Core experience

- Java 17-compatible JVM application with Swing + FlatLaf
- Cinematic desktop navigation with Home, Movies, Series, Live TV and Mi lista
- Home discovery rails for Horror, Thriller, Drama and Comedy
- Streaming-style rail navigation with visible previous/next controls and contextual horizontal wheel zones
- "Ver mÃ¡s" actions route Home shelves into filtered Movie/Series catalogs
- Movie and Series catalog filters for popular content and genres
- Unified TMDb catalog with **English (en-US)** or **Spanish (es-ES)** selected from Settings
- TMDb API key or Read Access Token configured locally from Settings
- High-resolution TMDb posters/backdrops with HiDPI-aware image rendering
- Debounced global search, in-app title details, seasons, specials and episodes
- Visible horizontal season navigation
- Streaming-style episode rows with stills, synopsis and playback progress
- Continue Watching with persistent movie/episode progress and resume
- Persistent Mi lista/history under %APPDATA%\\Streamflix
- Atomic settings/userdata writes and test data-directory isolation
- Progressive catalog loading while scrolling

### Playback

- Bundled portable mpv as the playback engine
- mpv video embedded inside Streamflix rather than exposing the external mpv UI
- Real fullscreen acquisition on Windows
- Loading state while extraction/player startup is in progress
- Automatic server fallback based on playback startup, not extraction success alone
- Local server ranking learns which hosts start faster and fail less on this machine
- Manual server selection
- Playback headers forwarded to mpv
- Pause/resume, seek Â±10 s, timeline, volume, audio-track and subtitle selection
- Stable windowed player chrome; controls auto-hide only in fullscreen
- Configurable quality: Automatic, Data Saver, Balanced, High and Maximum
- Automatic HLS quality starts below mpv's maximum-bitrate default and can downgrade after sustained buffering
- Configurable preferred audio and subtitle language
- TMDb playback currently certified through VixSrc for movies and episodes in EN/ES

### Catalog/provider inventory

Metadata / VOD:

- TMDb (EN)
- TMDb (ES)
- FanPelis
- RidoMovies
- PelisflixHD
- AnimeWorld
- Series Turcas
- La Cartoons
- AnimeSaturn
- AnimeUnity
- MEGAKino

Live TV:

- IPTV Spain
- IPTV All World
- Pluto TV MX
- Pluto TV ES
- Pluto TV US

Third-party providers can change or disappear without notice. A provider is not considered permanently healthy merely because it worked in a previous release.

## TMDb configuration

Streamflix accepts either:

1. STREAMFLIX_TMDB_API_KEY
2. %APPDATA%\\Streamflix\\settings.json â†’ tmdbApiKey

The environment variable has precedence.

The public desktop build currently uses a **BYOK (bring your own key)** model: each user obtains and configures their own TMDb API key or Read Access Token once. One credential works for both TMDb EN and TMDb ES. No real API key is stored in the repository or public binaries.

Settings includes an in-app step-by-step TMDb setup guide with direct links and editable/copyable examples for legitimate personal, educational/open-source or testing use.

## Build requirements

- Windows
- Complete JDK 17+ (java, javac, jar)
- jpackage
- mpv (tools\\mpv\\mpv.exe or STREAMFLIX_MPV)

The build verifies locked dependencies before compilation.

### Build the app image

PowerShell:

    .\\build.ps1

Output:

    dist\\StreamflixDesktop\\StreamflixDesktop.exe

### Build JAR only

    .\\build.ps1 -JarOnly

### Create portable release ZIP + SHA-256

    .\\release.ps1

The release script builds from source, runs the packaged --self-test, creates the portable ZIP and writes its SHA-256 file.

## Deterministic test gates

The build currently runs:

- JsonTest
- ProviderFixtureTest
- TmdbFixtureTest
- M3uPlaylistTest
- M3uLiveProviderTest
- ExtractorFixtureTest
- DependencySmokeTest
- UserDataTest
- MpvPlayerTest
- PlaybackFallbackTest
- PlaybackServerStatsTest

Additional opt-in live gates validate real third-party/network behavior and are intentionally not part of deterministic builds.

## Architecture

    Swing desktop UI
          |
          +--> ProviderRegistry
          |      |-- TMDb EN / ES
          |      |-- VOD providers
          |      \`-- M3U live providers
          |
          +--> Server discovery / fallback
          |
          +--> ExtractorRegistry
          |
          +--> mpv embedded window + JSON IPC
          |
          \`--> %APPDATA%\\Streamflix
                 |-- settings.json
                 |-- favorites.json
                 \`-- history.json

## Known limitations

- TMDb has one certified playback route in this release candidate (VixSrc); additional independent TMDb playback routes remain desirable for resilience.
- Some upstream providers rely on unstable public websites and may require maintenance after domain/HTML changes.
- Chromecast/casting parity is not included in the Windows release.
- Supabase/user-profile sync from Android is not included.
- DRM/paywall bypass is intentionally out of scope.

## Upstream and license

This project derives/ports functionality from Streamflix Reborn and retains the applicable Apache 2.0 licensing/attribution. See LICENSE and THIRD_PARTY_NOTICES.md.

The application does not host media. Users are responsible for ensuring they have the right to access content supplied by third-party sources.
