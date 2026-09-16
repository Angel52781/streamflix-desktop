package dev.streamflix.desktop;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

final class SelfTest {
    private static final Path REPORT = Path.of(
            System.getProperty("java.io.tmpdir"), "streamflix-desktop-selftest.txt");
    private static final Map<String, String> SEARCH_QUERIES = Map.of(
            "FanPelis", "Patrol",
            "RidoMovies", "Hell",
            "PelisflixHD", "Hugo",
            "AnimeWorld", "Dark",
            "Series Turcas", "Taht",
            "La Cartoons", "Perros",
            "AnimeSaturn", "Solo",
            "AnimeUnity", "Mou",
            "MEGAKino", "Trockenzeit"
    );

    private SelfTest() {}

    static int run() {
        StringBuilder report = new StringBuilder();
        try {
            Files.deleteIfExists(REPORT);
            if (!MpvPlayer.isAvailable()) throw new IllegalStateException("mpv missing");
            ExtractorRegistry registry = new ExtractorRegistry();
            report.append("SELF_TEST_OK\n")
                    .append("time=").append(Instant.now()).append('\n')
                    .append("mpv=OK\n");

            List<Provider> providers = ProviderRegistry.all();
            int passed = 0;
            for (Provider provider : providers) {
                String result = testProvider(provider, registry);
                report.append("provider=").append(provider.name())
                        .append(' ').append(result).append('\n');
                passed++;
            }
            report.append("providers=").append(passed).append('/').append(providers.size()).append('\n');
            writeReport(report.toString());
            System.out.print(report);
            return 0;
        } catch (Exception ex) {
            String failed = "SELF_TEST_FAILED\n"
                    + "time=" + Instant.now() + "\n"
                    + "error=" + ex + "\n"
                    + report;
            writeReport(failed);
            ex.printStackTrace(System.err);
            return 1;
        }
    }

    private static String testProvider(Provider provider, ExtractorRegistry registry) throws Exception {
        List<Models.ShowItem> movies = provider.supportsMovies() ? provider.movies(1) : List.of();
        List<Models.ShowItem> shows = provider.supportsTvShows() ? provider.tvShows(1) : List.of();
        if (movies.isEmpty() && shows.isEmpty()) {
            throw new IllegalStateException(provider.name() + ": empty catalog");
        }

        Models.ShowItem sample = !movies.isEmpty() ? movies.get(0) : shows.get(0);
        String posterStatus = "SKIP";
        if (sample.poster() != null && !sample.poster().isBlank()) {
            var image = ImageLoader.download(sample.poster());
            if (image.getWidth() < 1 || image.getHeight() < 1) {
                throw new IllegalStateException(provider.name() + ": poster decode failed");
            }
            posterStatus = image.getWidth() + "x" + image.getHeight();
        }

        String query = SEARCH_QUERIES.getOrDefault(provider.name(), firstSearchToken(sample.title()));
        List<Models.ShowItem> search = provider.search(query, 1);
        if (search.isEmpty()) {
            throw new IllegalStateException(provider.name() + ": search returned no results for " + query);
        }

        String playback = null;
        Exception movieError = null;
        if (!movies.isEmpty()) {
            try { playback = playableMovie(provider, movies, registry); }
            catch (Exception ex) { movieError = ex; }
        }
        if (playback == null && !shows.isEmpty()) {
            try { playback = playableSeries(provider, shows, registry); }
            catch (Exception seriesError) {
                if (movieError != null) seriesError.addSuppressed(movieError);
                throw seriesError;
            }
        }
        if (playback == null) throw new IllegalStateException(provider.name() + ": no playable item", movieError);

        return "movies=" + movies.size() + " series=" + shows.size()
                + " search=" + search.size() + " poster=" + posterStatus
                + " playback=" + playback;
    }

    private static String playableMovie(Provider provider, List<Models.ShowItem> movies,
                                        ExtractorRegistry registry) throws Exception {
        Exception last = null;
        for (Models.ShowItem movie : movies.stream().limit(6).toList()) {
            try {
                List<Models.Server> servers = provider.servers(movie.providerId());
                if (servers.isEmpty()) continue;
                String server = firstPlayable(servers, registry);
                return "movie:" + compact(movie.title()) + " via " + server;
            } catch (Exception ex) { last = ex; }
        }
        throw new IllegalStateException(provider.name() + ": no playable movie", last);
    }

    private static String playableSeries(Provider provider, List<Models.ShowItem> shows,
                                         ExtractorRegistry registry) throws Exception {
        Exception last = null;
        for (Models.ShowItem show : shows.stream().limit(6).toList()) {
            List<Models.Episode> episodes;
            try { episodes = provider.episodes(show); }
            catch (Exception ex) { last = ex; continue; }
            for (Models.Episode episode : episodes.stream().limit(6).toList()) {
                try {
                    List<Models.Server> servers = provider.servers(episode.id());
                    if (servers.isEmpty()) continue;
                    String server = firstPlayable(servers, registry);
                    return "series:" + compact(show.title()) + " T" + episode.seasonNumber()
                            + "E" + episode.episodeNumber() + " via " + server;
                } catch (Exception ex) { last = ex; }
            }
        }
        throw new IllegalStateException(provider.name() + ": no playable series episode", last);
    }

    private static String firstPlayable(List<Models.Server> servers, ExtractorRegistry registry) throws Exception {
        Exception last = null;
        for (Models.Server server : servers) {
            try {
                Models.Video video = registry.resolve(server);
                int exit = MpvPlayer.smoke(video);
                if (exit == 0) return server.name();
                last = new IllegalStateException("mpv exit " + exit + " on " + server.name());
            } catch (Exception ex) {
                last = ex;
            }
        }
        throw new IllegalStateException("No compatible playable server", last);
    }

    private static String firstSearchToken(String title) {
        if (title == null || title.isBlank()) return "a";
        for (String token : title.split("\\s+")) {
            String clean = token.replaceAll("[^\\p{L}\\p{N}]", "");
            if (clean.length() >= 3) return clean;
        }
        return title.trim();
    }

    private static String compact(String value) {
        if (value == null) return "unknown";
        String text = value.replace('\n', ' ').replace('\r', ' ').trim();
        return text.length() <= 50 ? text : text.substring(0, 47) + "...";
    }

    private static void writeReport(String value) {
        try {
            Files.writeString(REPORT, value, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {}
    }
}
