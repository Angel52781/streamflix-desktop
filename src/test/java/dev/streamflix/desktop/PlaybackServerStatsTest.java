package dev.streamflix.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PlaybackServerStatsTest {
    public static void main(String[] args) throws Exception {
        Path dataDir = Files.createTempDirectory("streamflix-server-stats-");
        String previous = System.getProperty("streamflix.data.dir");
        System.setProperty("streamflix.data.dir", dataDir.toString());

        try {
            Models.Server slow = new Models.Server(
                    "slow", "Slow CDN", "https://slow.example/video/embed");
            Models.Server fast = new Models.Server(
                    "fast", "Fast CDN", "https://fast.example/video/embed");

            List<Models.Server> unknown = PlaybackServerStats.rank(List.of(slow, fast));
            require(unknown.size() == 2, "unknown servers preserved");

            PlaybackServerStats.recordSuccess(slow, 4800);
            PlaybackServerStats.recordSuccess(fast, 900);
            List<Models.Server> learned = PlaybackServerStats.rank(List.of(slow, fast));
            require(learned.get(0).equals(fast), "faster successful host preferred");

            PlaybackServerStats.recordFailure(fast, 1000);
            PlaybackServerStats.recordFailure(fast, 1000);
            PlaybackServerStats.recordFailure(fast, 1000);
            List<Models.Server> penalized = PlaybackServerStats.rank(List.of(slow, fast));
            require(penalized.get(0).equals(slow), "repeated failures penalize host");

            require("slow.example".equals(PlaybackServerStats.key(slow)), "host key used");
            System.out.println("PlaybackServerStatsTest OK");
        } finally {
            if (previous == null) System.clearProperty("streamflix.data.dir");
            else System.setProperty("streamflix.data.dir", previous);

            Files.deleteIfExists(dataDir.resolve("server-stats.json"));
            Files.deleteIfExists(dataDir);
        }
    }

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError("Failed: " + name);
    }
}
