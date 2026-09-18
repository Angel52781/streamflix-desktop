package dev.streamflix.desktop;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Packaged application health checks.
 *
 * Deterministic mode is used by release.ps1 and never depends on third-party
 * network availability. Live mode is opt-in and checks real providers with
 * bounded time per provider.
 */
final class SelfTest {
    private static final Path REPORT = Path.of(
            System.getProperty("java.io.tmpdir"), "streamflix-desktop-selftest.txt");
    private static final long PROVIDER_TIMEOUT_SECONDS = 55;

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
            report.append("SELF_TEST_OK\n")
                    .append("mode=deterministic\n")
                    .append("time=").append(Instant.now()).append('\n');

            if (!MpvPlayer.isAvailable()) throw new IllegalStateException("mpv missing");
            report.append("mpv=OK\n");

            verifyRuntimeDependencies(report);
            verifyProviders(report);
            verifyPackagedVersion(report);

            writeReport(report.toString());
            System.out.print(report);
            return 0;
        } catch (Exception ex) {
            return fail(report, ex);
        }
    }

    static int runLive() {
        StringBuilder report = new StringBuilder();
        try {
            Files.deleteIfExists(REPORT);
            if (!MpvPlayer.isAvailable()) throw new IllegalStateException("mpv missing");

            ExtractorRegistry registry = new ExtractorRegistry();
            List<Provider> providers = ProviderRegistry.all();

            report.append("LIVE_SELF_TEST\n")
                    .append("time=").append(Instant.now()).append('\n')
                    .append("mpv=OK\n");

            int passed = 0;
            int failed = 0;
            for (Provider provider : providers) {
                System.out.println("Testing provider: " + provider.name());
                try {
                    String result = testProviderBounded(provider, registry);
                    report.append("provider=").append(provider.name())
                            .append(" OK ").append(result).append('\n');
                    passed++;
                } catch (Exception ex) {
                    report.append("provider=").append(provider.name())
                            .append(" FAIL ").append(compact(ex.toString())).append('\n');
                    failed++;
                } finally {
                    try { MpvPlayer.stopCurrent(); } catch (Exception ignored) {}
                }
            }

            report.append("providers_passed=").append(passed).append('/').append(providers.size()).append('\n')
                    .append("providers_failed=").append(failed).append('/').append(providers.size()).append('\n');
            writeReport(report.toString());
            System.out.print(report);
            return failed == 0 ? 0 : 2;
        } catch (Exception ex) {
            return fail(report, ex);
        }
    }

    private static void verifyRuntimeDependencies(StringBuilder report) throws Exception {
        String[] classes = {
                "com.formdev.flatlaf.FlatDarkLaf",
                "com.sun.jna.Native",
                "org.jsoup.Jsoup",
                "com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi"
        };
        for (String name : classes) {
            Class.forName(name);
            report.append("dependency=").append(name).append(" OK\n");
        }
    }

    private static void verifyProviders(StringBuilder report) {
        List<Provider> providers = ProviderRegistry.all();
        if (providers.isEmpty()) throw new IllegalStateException("provider registry empty");

        Set<String> ids = new HashSet<>();
        for (Provider provider : providers) {
            if (provider.id() == null || provider.id().isBlank()) {
                throw new IllegalStateException("provider without id: " + provider.name());
            }
            if (!ids.add(provider.id())) {
                throw new IllegalStateException("duplicate provider id: " + provider.id());
            }
            report.append("provider=").append(provider.id())
                    .append(" name=").append(provider.name())
                    .append(" movies=").append(provider.supportsMovies())
                    .append(" tv=").append(provider.supportsTvShows())
                    .append('\n');
        }

        requireProvider(ids, "tmdb-en");
        requireProvider(ids, "tmdb-es");
        requireProvider(ids, "iptv-spain");
        requireProvider(ids, "pluto-es");
        report.append("providers=").append(providers.size()).append(" OK\n");
    }

    private static void requireProvider(Set<String> ids, String id) {
        if (!ids.contains(id)) throw new IllegalStateException("required provider missing: " + id);
    }

    private static void verifyPackagedVersion(StringBuilder report) throws Exception {
        String implementation = App.class.getPackage().getImplementationVersion();
        if (implementation == null || implementation.isBlank()) {
            throw new IllegalStateException("Implementation-Version missing");
        }
        report.append("implementation_version=").append(implementation).append('\n');

        String appPath = System.getProperty("jpackage.app-path");
        if (appPath == null || appPath.isBlank()) {
            report.append("packaged_version_file=SKIP non-jpackage runtime\n");
            return;
        }

        Path appDir = Path.of(appPath).toAbsolutePath().getParent();
        if (appDir == null) throw new IllegalStateException("jpackage app directory unavailable");
        Path versionFile = appDir.resolve("VERSION");
        if (!Files.isRegularFile(versionFile)) throw new IllegalStateException("packaged VERSION missing");
        String packaged = Files.readString(versionFile).trim();
        if (!implementation.equals(packaged)) {
            throw new IllegalStateException("version mismatch: manifest=" + implementation + " file=" + packaged);
        }
        report.append("packaged_version_file=").append(packaged).append(" OK\n");

        if (!PortableUpdater.canSelfUpdate()) {
            throw new IllegalStateException("portable updater cannot replace this app image");
        }
        report.append("portable_updater=OK\n");

        Path mpvNotices = appDir.resolve("third_party").resolve("mpv");
        for (String required : List.of("Copyright", "LICENSE.GPL", "LICENSE.LGPL", "SOURCE.txt")) {
            if (!Files.isRegularFile(mpvNotices.resolve(required))) {
                throw new IllegalStateException("packaged mpv notice missing: " + required);
            }
        }
        report.append("mpv_notices=OK\n");
    }

    private static String testProviderBounded(Provider provider, ExtractorRegistry registry) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "selftest-" + provider.id());
            thread.setDaemon(true);
            return thread;
        });
        Future<String> future = executor.submit(() -> testProvider(provider, registry));
        try {
            return future.get(PROVIDER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new TimeoutException(provider.name() + ": timeout after "
                    + PROVIDER_TIMEOUT_SECONDS + "s");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) throw exception;
            throw new RuntimeException(cause);
        } finally {
            executor.shutdownNow();
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
        if (playback == null) {
            throw new IllegalStateException(provider.name() + ": no playable item", movieError);
        }

        return "movies=" + movies.size() + " series=" + shows.size()
                + " search=" + search.size() + " poster=" + posterStatus
                + " playback=" + playback;
    }

    private static String playableMovie(Provider provider, List<Models.ShowItem> movies,
                                        ExtractorRegistry registry) throws Exception {
        Exception last = null;
        for (Models.ShowItem movie : movies.stream().limit(6).toList()) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            try {
                List<Models.Server> servers = provider.servers(movie.providerId());
                if (servers.isEmpty()) continue;
                String server = firstPlayable(servers, registry);
                return "movie:" + compact(movie.title()) + " via " + server;
            } catch (Exception ex) {
                last = ex;
            }
        }
        throw new IllegalStateException(provider.name() + ": no playable movie", last);
    }

    private static String playableSeries(Provider provider, List<Models.ShowItem> shows,
                                         ExtractorRegistry registry) throws Exception {
        Exception last = null;
        for (Models.ShowItem show : shows.stream().limit(6).toList()) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            List<Models.Episode> episodes;
            try {
                episodes = provider.episodes(show);
            } catch (Exception ex) {
                last = ex;
                continue;
            }
            for (Models.Episode episode : episodes.stream().limit(6).toList()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                try {
                    List<Models.Server> servers = provider.servers(episode.id());
                    if (servers.isEmpty()) continue;
                    String server = firstPlayable(servers, registry);
                    return "series:" + compact(show.title()) + " T" + episode.seasonNumber()
                            + "E" + episode.episodeNumber() + " via " + server;
                } catch (Exception ex) {
                    last = ex;
                }
            }
        }
        throw new IllegalStateException(provider.name() + ": no playable series episode", last);
    }

    private static String firstPlayable(List<Models.Server> servers, ExtractorRegistry registry) throws Exception {
        Exception last = null;
        for (Models.Server server : servers) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
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
        return text.length() <= 120 ? text : text.substring(0, 117) + "...";
    }

    private static int fail(StringBuilder report, Exception ex) {
        String failed = "SELF_TEST_FAILED\n"
                + "time=" + Instant.now() + "\n"
                + "error=" + ex + "\n"
                + report;
        writeReport(failed);
        ex.printStackTrace(System.err);
        return 1;
    }

    private static void writeReport(String value) {
        try {
            Files.writeString(REPORT, value,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {}
    }
}
