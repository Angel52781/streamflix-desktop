package dev.streamflix.desktop;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class PlaybackFallbackTest {
    public static void main(String[] args) throws Exception {
        testFallsBackWhenPlayerStartupFails();
        testFallsBackWhenExtractionFails();
        testStopsAfterFirstSuccessfulServer();
        System.out.println("PlaybackFallbackTest OK");
    }

    private static void testFallsBackWhenPlayerStartupFails() throws Exception {
        Models.Server first = new Models.Server("one", "Server One", "https://one.example/embed");
        Models.Server second = new Models.Server("two", "Server Two", "https://two.example/embed");
        AtomicInteger starts = new AtomicInteger();

        Models.Server selected = PlaybackFallback.startFirstAvailable(
                List.of(first, second),
                "Demo",
                server -> new Models.Video("https://cdn.example/" + server.id() + ".m3u8"),
                (video, title) -> {
                    if (starts.getAndIncrement() == 0) throw new IOException("mpv startup failed");
                }
        );

        require(selected == second, "second server selected after startup failure");
        require(starts.get() == 2, "both candidates attempted");
    }

    private static void testFallsBackWhenExtractionFails() throws Exception {
        Models.Server first = new Models.Server("bad", "Bad Extractor", "https://bad.example/embed");
        Models.Server second = new Models.Server("good", "Good Extractor", "https://good.example/embed");
        AtomicInteger resolved = new AtomicInteger();
        AtomicInteger starts = new AtomicInteger();

        Models.Server selected = PlaybackFallback.startFirstAvailable(
                List.of(first, second),
                "Demo",
                server -> {
                    resolved.incrementAndGet();
                    if (server == first) throw new IOException("extractor failed");
                    return new Models.Video("https://cdn.example/good.m3u8");
                },
                (video, title) -> starts.incrementAndGet()
        );

        require(selected == second, "second server selected after extractor failure");
        require(resolved.get() == 2, "both candidates resolved");
        require(starts.get() == 1, "player starts only resolved candidate");
    }

    private static void testStopsAfterFirstSuccessfulServer() throws Exception {
        Models.Server first = new Models.Server("one", "One", "https://one.example/embed");
        Models.Server second = new Models.Server("two", "Two", "https://two.example/embed");
        AtomicInteger resolved = new AtomicInteger();

        Models.Server selected = PlaybackFallback.startFirstAvailable(
                List.of(first, second),
                "Demo",
                server -> {
                    resolved.incrementAndGet();
                    return new Models.Video("https://cdn.example/" + server.id() + ".m3u8");
                },
                (video, title) -> { }
        );

        require(selected == first, "first healthy server selected");
        require(resolved.get() == 1, "fallback loop stops after success");
    }

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError("Failed: " + name);
    }
}
