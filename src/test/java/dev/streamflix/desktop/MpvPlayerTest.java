package dev.streamflix.desktop;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class MpvPlayerTest {
    public static void main(String[] args) throws Exception {
        Path settingsDir = Files.createTempDirectory("streamflix-mpv-settings-");
        System.setProperty("streamflix.data.dir", settingsDir.toString());
        try {
            testCommandHeadersAndSubtitlePreference();
            testPlaybackPreferenceOverrides();
            testEmbeddedCommand();
            testPlaybackNetworkProfile();
            testProgrammaticTimelineRefreshDoesNotSeek();
            testImmediateFailureRejected();
            testNewPlaybackStopsPrevious();
            testStaleRequestCannotReplaceNewerIntent();
            testShutdownPreventsRestart();
            System.out.println("MpvPlayerTest OK");
        } finally {
            Files.deleteIfExists(settingsDir.resolve("settings.json"));
            Files.deleteIfExists(settingsDir);
            System.clearProperty("streamflix.data.dir");
        }
    }

    private static void testCommandHeadersAndSubtitlePreference() {
        Models.Video video = new Models.Video(
                "https://cdn.example/video.m3u8",
                Map.of("User-Agent", "Agent X", "Referer", "https://embed.example", "X-Test", "one,two"),
                List.of(
                        new Models.Subtitle("English", "en.vtt", false),
                        new Models.Subtitle("Español", "es.vtt", false),
                        new Models.Subtitle("Director default", "default.vtt", true)
                )
        );
        List<String> command = MpvPlayer.playbackCommand("mpv.exe", video, "Title\nLine");
        require(command.contains("--user-agent=Agent X"), "user agent forwarded");
        require(command.contains("--referrer=https://embed.example/"), "referrer normalized");
        require(command.contains("--http-header-fields=X-Test: one\\,two"), "extra header forwarded");
        require(command.contains("--slang=es,spa,es-ES,es-419,en,eng"), "subtitle language preference");
        int d = command.indexOf("--sub-file=default.vtt");
        int es = command.indexOf("--sub-file=es.vtt");
        int en = command.indexOf("--sub-file=en.vtt");
        require(d >= 0 && d < es && es < en, "default then Spanish then English subtitle order");
        require(command.contains("--force-media-title=Title Line"), "title sanitized");
    }

    private static void testPlaybackPreferenceOverrides() {
        PlaybackSettings.save(true, "en", "off");
        Models.Video video = new Models.Video(
                "https://cdn.example/video.m3u8",
                Map.of(),
                List.of(
                        new Models.Subtitle("English", "en.vtt", false),
                        new Models.Subtitle("Español", "es.vtt", false)
                )
        );

        List<String> command = MpvPlayer.playbackCommand("mpv.exe", video, "Preferences");
        require(command.contains("--alang=en,eng,es,spa,es-ES,es-419"),
                "English audio preference forwarded");
        require(command.contains("--sid=no"), "subtitles can default to disabled");

        PlaybackSettings.save(true, "auto", "es");
    }

    private static void testEmbeddedCommand() {
        Models.Video video = new Models.Video("https://cdn.example/video.m3u8");
        List<String> command = MpvPlayer.embeddedPlaybackCommand(
                "mpv.exe", video, "Embedded", 12345L, "\\\\.\\pipe\\streamflix-test");
        require(command.contains("--wid=12345"), "embedded HWND forwarded");
        require(command.contains("--input-ipc-server=\\\\.\\pipe\\streamflix-test"), "embedded IPC pipe forwarded");
        require(command.contains("--osc=no"), "external mpv OSC disabled");
        require(command.contains("--input-default-bindings=no"), "external bindings disabled");
        require(command.contains("--keep-open=yes"), "embedded playback stays attached");
    }

    private static void testPlaybackNetworkProfile() {
        Models.Video video = new Models.Video("https://cdn.example/video.m3u8");
        List<String> command = MpvPlayer.embeddedPlaybackCommand(
                "mpv.exe", video, "Embedded", 12345L, "\\\\.\\pipe\\streamflix-test");
        require(command.contains("--hwdec=auto-safe"), "hardware decoding enabled");
        require(command.contains("--network-timeout=20"), "dead network reads fail in bounded time");
        require(command.contains("--cache-pause=yes"), "network cache pause enabled");
        require(command.contains("--cache-pause-wait=2"), "cache recovery waits for useful buffer");
    }

    private static void testProgrammaticTimelineRefreshDoesNotSeek() {
        require(!EmbeddedPlayerWindow.shouldSeekTimeline(true, false, 2400),
                "programmatic timeline refresh never seeks");
        require(!EmbeddedPlayerWindow.shouldSeekTimeline(false, true, 2400),
                "dragging timeline waits until adjustment completes");
        require(!EmbeddedPlayerWindow.shouldSeekTimeline(false, false, 0),
                "timeline without duration cannot seek");
        require(EmbeddedPlayerWindow.shouldSeekTimeline(false, false, 2400),
                "user timeline selection can seek");
    }

    private static void testImmediateFailureRejected() throws Exception {
        FakeProcess failed = FakeProcess.exited(2);
        MpvPlayer player = new MpvPlayer(() -> "mpv.exe", builder -> failed, 10, 10);
        try {
            player.start(new Models.Video("https://example.invalid/video.m3u8"), "Failure");
            throw new AssertionError("Expected immediate non-zero mpv exit to fail");
        } catch (IOException expected) {
            require(expected.getMessage().contains("mpv"), "startup error mentions mpv");
        }
    }

    private static void testNewPlaybackStopsPrevious() throws Exception {
        List<FakeProcess> launched = new ArrayList<>();
        MpvPlayer player = new MpvPlayer(() -> "mpv.exe", builder -> {
            FakeProcess process = FakeProcess.running();
            launched.add(process);
            return process;
        }, 5, 5);

        Models.Video one = new Models.Video("https://example.invalid/one.m3u8");
        Models.Video two = new Models.Video("https://example.invalid/two.m3u8");
        player.start(one, "One");
        require(launched.size() == 1 && launched.get(0).isAlive(), "first playback running");
        player.start(two, "Two");
        require(launched.size() == 2, "second playback launched");
        require(!launched.get(0).isAlive(), "previous playback stopped");
        require(launched.get(0).destroyCalls.get() >= 1, "previous playback destroy called");
        require(launched.get(1).isAlive(), "second playback remains active");
        player.close();
        require(!launched.get(1).isAlive(), "close stops active playback");
    }

    private static void testStaleRequestCannotReplaceNewerIntent() throws Exception {
        AtomicInteger launches = new AtomicInteger();
        MpvPlayer player = new MpvPlayer(() -> "mpv.exe", builder -> {
            launches.incrementAndGet();
            return FakeProcess.running();
        }, 5, 5);
        long oldRequest = player.newRequest();
        long newRequest = player.newRequest();
        try {
            player.start(new Models.Video("https://example.invalid/old.m3u8"), "Old", oldRequest);
            throw new AssertionError("Expected stale request cancellation");
        } catch (RuntimeException expected) {
            require(expected.getClass().getSimpleName().contains("Cancellation"), "stale request cancelled");
        }
        require(launches.get() == 0, "stale request never launches player");
        player.start(new Models.Video("https://example.invalid/new.m3u8"), "New", newRequest);
        require(launches.get() == 1, "newest request launches once");
        player.close();
    }

    private static void testShutdownPreventsRestart() throws Exception {
        MpvPlayer player = new MpvPlayer(() -> "mpv.exe", builder -> FakeProcess.running(), 5, 5);
        player.close();
        try {
            player.start(new Models.Video("https://example.invalid/video.m3u8"), "Closed");
            throw new AssertionError("Expected closed player to reject restart");
        } catch (RuntimeException expected) {
            require(expected.getClass().getSimpleName().contains("Cancellation"), "closed player cancels restart");
        }
    }

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError("Failed: " + name);
    }

    private static final class FakeProcess extends Process {
        final AtomicInteger destroyCalls = new AtomicInteger();
        private volatile boolean alive;
        private volatile int exitCode;

        static FakeProcess running() { return new FakeProcess(true, 0); }
        static FakeProcess exited(int code) { return new FakeProcess(false, code); }

        FakeProcess(boolean alive, int exitCode) {
            this.alive = alive;
            this.exitCode = exitCode;
        }

        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() { alive = false; return exitCode; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return !alive; }
        @Override public int exitValue() {
            if (alive) throw new IllegalThreadStateException();
            return exitCode;
        }
        @Override public void destroy() { destroyCalls.incrementAndGet(); alive = false; }
        @Override public Process destroyForcibly() { destroyCalls.incrementAndGet(); alive = false; return this; }
        @Override public boolean isAlive() { return alive; }
    }
}
