package dev.streamflix.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class TmdbFixtureTest {
    public static void main(String[] args) throws Exception {
        testSettingsPrecedence();
        testSettingsSaveUsesIsolatedDataDir();
        testMissingKeyFailsBeforeTransport();
        testAuthenticationModes();
        testMovieAndSearchMapping();
        testCrossLanguageSearchFallback();
        testGenreListing();
        testEpisodesAcrossSeasons();
        testSpanishIdentity();
        testPlaybackServers();
        System.out.println("TmdbFixtureTest OK");
    }

    private static void testSettingsPrecedence() throws Exception {
        Path appData = Files.createTempDirectory("streamflix-tmdb-settings-");
        Path dir = appData.resolve("Streamflix");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("settings.json"), "{\"tmdbApiKey\":\" file-key \"}");
        try {
            Map<String, String> env = Map.of("APPDATA", appData.toString(), "STREAMFLIX_TMDB_API_KEY", " env-key ");
            require("env-key".equals(TmdbSettings.apiKey(env, appData.toString())), "environment key precedence");
            require("file-key".equals(TmdbSettings.apiKey(Map.of("APPDATA", appData.toString()), appData.toString())), "settings key fallback");
        } finally {
            Files.deleteIfExists(dir.resolve("settings.json"));
            Files.deleteIfExists(dir);
            Files.deleteIfExists(appData);
        }
    }

    private static void testSettingsSaveUsesIsolatedDataDir() throws Exception {
        Path dataDir = Files.createTempDirectory("streamflix-tmdb-save-");
        String previous = System.getProperty("streamflix.data.dir");
        System.setProperty("streamflix.data.dir", dataDir.toString());
        try {
            TmdbSettings.saveApiKey("0123456789abcdef0123456789abcdef");
            require("0123456789abcdef0123456789abcdef".equals(TmdbSettings.localApiKey()), "saved local key");
            String raw = Files.readString(dataDir.resolve("settings.json"));
            require(raw.contains("\"tmdbApiKey\""), "settings field persisted");

            PlaybackSettings.save(false, "en", "off", "balanced");
            require("0123456789abcdef0123456789abcdef".equals(TmdbSettings.localApiKey()),
                    "playback settings preserve TMDb credential");
            require(!PlaybackSettings.startMaximized(), "window preference persisted");
            require("en".equals(PlaybackSettings.audioLanguage()), "audio preference persisted");
            require("off".equals(PlaybackSettings.subtitleLanguage()), "subtitle preference persisted");
            require("balanced".equals(PlaybackSettings.qualityProfile()), "quality preference persisted");

            TmdbSettings.saveApiKey("");
            require(TmdbSettings.localApiKey().isBlank(), "empty key removes local setting");
        } finally {
            if (previous == null) System.clearProperty("streamflix.data.dir");
            else System.setProperty("streamflix.data.dir", previous);
            Files.deleteIfExists(dataDir.resolve("settings.json"));
            Files.deleteIfExists(dataDir);
        }
    }

    private static void testMissingKeyFailsBeforeTransport() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TmdbClient client = new TmdbClient("en", () -> "", (url, headers) -> {
            calls.incrementAndGet();
            return "{}";
        });
        try {
            client.get("discover/movie", Map.of("page", "1"));
            throw new AssertionError("Expected missing API key error");
        } catch (TmdbException ex) {
            require(ex.getMessage().contains("API key missing"), "missing key message");
            require(calls.get() == 0, "transport not called without key");
        }
    }

    private static void testAuthenticationModes() throws Exception {
        AtomicInteger apiKeyCalls = new AtomicInteger();
        TmdbClient apiKeyClient = new TmdbClient("en", () -> "0123456789abcdef0123456789abcdef", (url, headers) -> {
            require(url.contains("api_key=0123456789abcdef0123456789abcdef"), "v3 API key query");
            require(!headers.containsKey("Authorization"), "v3 API key has no bearer header");
            apiKeyCalls.incrementAndGet();
            return "{\"results\":[]}";
        });
        apiKeyClient.get("discover/movie", Map.of("page", "1"));
        require(apiKeyCalls.get() == 1, "v3 API key transport called");

        AtomicInteger bearerCalls = new AtomicInteger();
        String token = "eyJhbGciOiJIUzI1NiJ9.fixture.signature";
        TmdbClient bearerClient = new TmdbClient("en", () -> token, (url, headers) -> {
            require(!url.contains("api_key="), "bearer token not placed in URL");
            require(("Bearer " + token).equals(headers.get("Authorization")), "bearer header");
            bearerCalls.incrementAndGet();
            return "{\"results\":[]}";
        });
        bearerClient.get("discover/movie", Map.of("page", "1"));
        require(bearerCalls.get() == 1, "bearer transport called");
    }

    private static void testMovieAndSearchMapping() throws Exception {
        TmdbClient client = client("en", url -> {
            if (url.contains("discover/movie")) return """
                    {"results":[{"id":11,"title":"Demo Movie","overview":"Movie overview","release_date":"2026-04-03","vote_average":7.9,"poster_path":"/poster.jpg","backdrop_path":"/backdrop.jpg"}]}
                    """;
            if (url.contains("search/multi")) return """
                    {"results":[
                      {"id":11,"media_type":"movie","title":"Demo Movie","release_date":"2026-04-03","vote_average":7.9,"poster_path":"/poster.jpg"},
                      {"id":22,"media_type":"tv","name":"Demo Show","first_air_date":"2025-01-02","vote_average":8.1,"poster_path":"/show.jpg"},
                      {"id":33,"media_type":"person","name":"Ignored Person"}
                    ]}
                    """;
            throw new IllegalArgumentException("Unexpected URL");
        });
        TmdbProvider provider = new TmdbProvider(client);
        List<Models.ShowItem> movies = provider.movies(1);
        require(movies.size() == 1, "movie count");
        Models.ShowItem movie = movies.get(0);
        require("tmdb:movie:11".equals(movie.id()), "movie logical id");
        require("movie/11".equals(movie.providerId()), "movie remote id");
        require("tmdb-en".equals(movie.sourceProviderId()), "movie source provider");
        require(movie.poster().equals("https://image.tmdb.org/t/p/w780/poster.jpg"), "movie poster");

        List<Models.ShowItem> search = provider.search("Demo", 1);
        require(search.size() == 2, "multi search filters people");
        require(search.get(0).type() == Models.ShowType.MOVIE, "search movie type");
        require(search.get(1).type() == Models.ShowType.TV_SHOW, "search tv type");
    }

    private static void testCrossLanguageSearchFallback() throws Exception {
        TmdbClient client = client("es", url -> {
            if (!url.contains("search/multi")) throw new IllegalArgumentException("Unexpected URL");
            if (url.contains("language=es-ES")) {
                return """
                        {"results":[
                          {"id":117581,"media_type":"tv","name":"Ginny y Georgia","first_air_date":"2021-02-24","vote_average":8.0}
                        ]}
                        """;
            }
            if (url.contains("language=en-US")) {
                return """
                        {"results":[
                          {"id":117581,"media_type":"tv","name":"Ginny & Georgia","first_air_date":"2021-02-24","vote_average":8.0},
                          {"id":999,"media_type":"tv","name":"English-only fallback","first_air_date":"2026-01-01","vote_average":7.0}
                        ]}
                        """;
            }
            throw new IllegalArgumentException("Missing language");
        });
        List<Models.ShowItem> results = new TmdbProvider(client).search("Ginny & Georgia", 1);
        require(results.size() == 2, "cross-language search merge");
        require("Ginny y Georgia".equals(results.get(0).title()),
                "preferred-language result wins duplicate canonical id");
        require("tmdb:tv:999".equals(results.get(1).id()),
                "fallback-only result remains discoverable");
        require("tmdb-es".equals(results.get(1).sourceProviderId()),
                "fallback result stays attached to preferred provider");
    }

    private static void testGenreListing() throws Exception {
        TmdbClient client = client("es", url -> {
            require(url.contains("with_genres=27"), "genre query forwarded");
            return """
                    {"results":[{"id":99,"title":"Terror demo","release_date":"2026-10-31","vote_average":7.1,"poster_path":"/terror.jpg"}]}
                    """;
        });
        TmdbProvider provider = new TmdbProvider(client);
        List<Models.ShowItem> horror = provider.moviesByGenre(27, 1);
        require(horror.size() == 1, "genre listing count");
        require("tmdb:movie:99".equals(horror.get(0).id()), "genre listing stable id");

        TmdbClient tvClient = client("es", url -> {
            require(url.contains("discover/tv"), "tv genre endpoint");
            require(url.contains("with_genres=18"), "tv genre query forwarded");
            return """
                    {"results":[{"id":199,"name":"Drama demo","first_air_date":"2026-09-01","vote_average":8.0,"poster_path":"/drama.jpg"}]}
                    """;
        });
        List<Models.ShowItem> drama = new TmdbProvider(tvClient).tvShowsByGenre(18, 1);
        require(drama.size() == 1 && drama.get(0).type() == Models.ShowType.TV_SHOW,
                "tv genre listing mapped");
    }

    private static void testEpisodesAcrossSeasons() throws Exception {
        TmdbClient client = client("en", url -> {
            if (url.contains("/tv/22/season/0")) return """
                    {"episodes":[{"episode_number":1,"name":"Special","overview":"Special overview","still_path":"/s0e1.jpg"}]}
                    """;
            if (url.contains("/tv/22/season/1")) return """
                    {"episodes":[
                      {"episode_number":1,"name":"Pilot","overview":"Pilot overview","still_path":"/s1e1.jpg"},
                      {"episode_number":2,"name":"Second","overview":"Second overview","still_path":null}
                    ]}
                    """;
            if (url.contains("/tv/22?")) return """
                    {"seasons":[{"season_number":1},{"season_number":0}]}
                    """;
            throw new IllegalArgumentException("Unexpected URL: " + url);
        });
        TmdbProvider provider = new TmdbProvider(client);
        Models.ShowItem show = new Models.ShowItem("tmdb:tv:22", "tv/22", "Demo Show", null, null, null, null, null, null, Models.ShowType.TV_SHOW, "tmdb-en");
        List<Models.Episode> episodes = provider.episodes(show);
        require(episodes.size() == 3, "episode count");
        require(episodes.get(0).seasonNumber() == 0 && episodes.get(0).episodeNumber() == 1, "special sorted first");
        require("tv/22/season/1/episode/2".equals(episodes.get(2).id()), "stable episode id");
    }

    private static void testPlaybackServers() throws Exception {
        TmdbProvider en = new TmdbProvider(client("en", url -> "{}"));
        List<Models.Server> movie = en.servers("movie/550");
        require(movie.size() == 1, "movie playback server count");
        require("VixSrc".equals(movie.get(0).name()), "movie playback server name");
        require(movie.get(0).src().equals("https://vixsrc.to/api/movie/550?lang=en"), "movie playback URL");

        List<Models.Server> episode = en.servers("tv/1399/season/1/episode/2");
        require(episode.size() == 1, "episode playback server count");
        require(episode.get(0).src().equals("https://vixsrc.to/api/tv/1399/1/2?lang=en"), "episode playback URL");

        TmdbProvider es = new TmdbProvider(client("es", url -> "{}"));
        require(es.servers("movie/550").get(0).src().endsWith("?lang=es"), "Spanish playback language");
    }

    private static void testSpanishIdentity() throws Exception {
        TmdbClient client = client("es", url -> """
                {"results":[{"id":77,"name":"Serie de prueba","first_air_date":"2026-02-01","vote_average":8.0,"poster_path":"/serie.jpg"}]}
                """);
        TmdbProvider provider = new TmdbProvider(client);
        require("TMDb (ES)".equals(provider.name()), "Spanish provider name");
        require("tmdb-es".equals(provider.id()), "Spanish provider id");
        List<Models.ShowItem> shows = provider.tvShows(1);
        require(shows.size() == 1 && "tmdb-es".equals(shows.get(0).sourceProviderId()), "Spanish source identity");
    }

    private static TmdbClient client(String language, UrlResponder responder) {
        return new TmdbClient(language, () -> "fixture-key", (url, headers) -> responder.respond(url));
    }

    @FunctionalInterface
    private interface UrlResponder {
        String respond(String url);
    }

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError("Failed: " + name);
    }
}
