package dev.streamflix.desktop;

import java.nio.file.*;
import java.time.Instant;
import java.util.List;

final class SelfTest {
    private static final Path REPORT = Path.of(
            System.getProperty("java.io.tmpdir"), "streamflix-desktop-selftest.txt");

    private SelfTest() {}

    static int run() {
        try {
            Files.deleteIfExists(REPORT);
            FanpelisProvider provider = new FanpelisProvider();
            ExtractorRegistry registry = new ExtractorRegistry();

            List<Models.ShowItem> movies = provider.movies(1);
            if (movies.isEmpty()) throw new IllegalStateException("Movie catalog is empty");
            Models.Video movieVideo = resolveFirst(provider.servers(movies.get(0).providerId()), registry);
            int movieMpvExit = MpvPlayer.smoke(movieVideo);
            if (movieMpvExit != 0) throw new IllegalStateException("mpv movie smoke failed: " + movieMpvExit);

            String seriesResult = testSeries(provider, registry);
            String report = "SELF_TEST_OK\n" +
                    "time=" + Instant.now() + "\n" +
                    "movie=" + movies.get(0).title() + "\n" +
                    "series=" + seriesResult + "\n" +
                    "mpv=OK\n";
            Files.writeString(REPORT, report, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.print(report);
            return 0;
        } catch (Exception ex) {
            try {
                Files.writeString(REPORT,
                        "SELF_TEST_FAILED\ntime=" + Instant.now() + "\nerror=" + ex + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (Exception ignored) {}
            ex.printStackTrace(System.err);
            return 1;
        }
    }

    private static String testSeries(FanpelisProvider provider, ExtractorRegistry registry) throws Exception {
        List<Models.ShowItem> shows = provider.tvShows(1);
        if (shows.isEmpty()) throw new IllegalStateException("TV catalog is empty");
        for (Models.ShowItem show : shows) {
            for (Models.Episode episode : provider.episodes(show)) {
                List<Models.Server> servers = provider.servers(episode.id());
                if (servers.isEmpty()) continue;
                try {
                    Models.Video episodeVideo = resolveFirst(servers, registry);
                    int mpvExit = MpvPlayer.smoke(episodeVideo);
                    if (mpvExit != 0) continue;
                    return show.title() + " T" + episode.seasonNumber() + "E" + episode.episodeNumber();
                } catch (Exception ignored) {}
            }
        }
        throw new IllegalStateException("No playable series episode found");
    }

    private static Models.Video resolveFirst(List<Models.Server> servers, ExtractorRegistry registry) throws Exception {
        Exception last = null;
        for (Models.Server server : servers) {
            try { return registry.resolve(server); }
            catch (Exception ex) { last = ex; }
        }
        throw new IllegalStateException("No compatible server resolved", last);
    }
}
