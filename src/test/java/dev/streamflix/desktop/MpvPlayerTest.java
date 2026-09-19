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
            testLanguageAliasesDoNotConfuseItalianWithSpanish();
            testPlaybackPreferenceOverrides();
            testEmbeddedCommand();
            testPlaybackNetworkProfile();
            testProgrammaticTimelineRefreshDoesNotSeek();
            testPlayerChromeOverlayKeepsMediaStable();
            testPlayerWindowBoundsStayInsideWorkArea();
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
        require(es >= 0 && es < en && en < d,
                "explicit Spanish preference outranks source default and English");
        require(command.contains("--force-media-title=Title Line"), "title sanitized");
        require(command.contains("--hls-bitrate=4000000"), "automatic quality caps HLS bitrate");
    }

    private static void testLanguageAliasesDoNotConfuseItalianWithSpanish() {
        require(MediaLanguage.matches("spa", "", "es"), "spa matches Spanish");
        require(MediaLanguage.matches("es-ES", "", "es"), "es-ES matches Spanish");
        require(MediaLanguage.matches("", "Spanish", "es"), "Spanish title matches Spanish");
        require(MediaLanguage.matches("", "Español Latino", "es"), "Español title matches Spanish");
        require(MediaLanguage.matches("eng", "", "en"), "eng matches English");
        require(MediaLanguage.matches("", "English", "en"), "English title matches English");
        require(!MediaLanguage.matches("ita", "Italiano", "es"),
                "Italian metadata must never match Spanish");
        require(!MediaLanguage.matches("it", "Italian", "es"),
                "Italian language code must never match Spanish");
    }

    private static void testPlaybackPreferenceOverrides() {
        PlaybackSettings.save(true, "en", "off", "max");
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
        require(command.contains("--hls-bitrate=max"), "maximum quality profile forwarded");

        PlaybackSettings.save(true, "auto", "es", "auto");
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

        List<String> saver = MpvPlayer.embeddedPlaybackCommand(
                "mpv.exe", video, "Embedded", 12345L, "\\\\.\\pipe\\streamflix-test", "1500000");
        require(saver.contains("--hls-bitrate=1500000"), "session bitrate override forwarded");
    }

    private static void testPlaybackNetworkProfile() {
        Models.Video video = new Models.Video("https://cdn.example/video.m3u8");
        List<String> command = MpvPlayer.embeddedPlaybackCommand(
                "mpv.exe", video, "Embedded", 12345L, "\\\\.\\pipe\\streamflix-test");
        require(command.contains("--hwdec=auto-safe"), "hardware decoding enabled");
        require(command.contains("--network-timeout=20"), "dead network reads fail in bounded time");
        require(command.contains("--cache-pause=yes"), "network cache pause enabled");
        require(command.contains("--cache-pause-wait=2"), "cache recovery waits for useful buffer");
        require(command.contains("--cache=yes"), "network cache forced on");
        require(command.contains("--cache-secs=45"), "bounded readahead window configured");
        require(command.contains("--stream-buffer-size=1MiB"), "larger network read buffer configured");
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

    private static void testPlayerChromeOverlayKeepsMediaStable() {
        javax.swing.JLayeredPane root = new javax.swing.JLayeredPane();
        javax.swing.JPanel media = new javax.swing.JPanel();
        javax.swing.JPanel top = new javax.swing.JPanel();
        javax.swing.JPanel bottom = new javax.swing.JPanel();

        root.setSize(1920, 1080);
        top.setPreferredSize(new java.awt.Dimension(1920, 64));
        bottom.setPreferredSize(new java.awt.Dimension(1920, 96));

        EmbeddedPlayerWindow.layoutPlayerLayers(root, media, top, bottom);
        java.awt.Rectangle before = media.getBounds();

        top.setVisible(false);
        bottom.setVisible(false);
        EmbeddedPlayerWindow.layoutPlayerLayers(root, media, top, bottom);

        require(before.equals(media.getBounds()),
                "hiding player chrome must never resize the video surface");
        require(before.equals(new java.awt.Rectangle(0, 0, 1920, 1080)),
                "video surface always occupies the full player area");
    }

    private static void testPlayerWindowBoundsStayInsideWorkArea() {
        java.awt.Rectangle work = new java.awt.Rectangle(0, 0, 1366, 728);
        java.awt.Dimension minimum = new java.awt.Dimension(760, 460);

        java.awt.Rectangle fitted = EmbeddedPlayerWindow.fitBoundsToWorkArea(
                new java.awt.Rectangle(-120, 40, 1320, 820), work, minimum);
        require(fitted.x >= work.x && fitted.y >= work.y,
                "player window top-left remains inside usable work area");
        require(fitted.x + fitted.width <= work.x + work.width,
                "player window right edge remains inside usable work area");
        require(fitted.y + fitted.height <= work.y + work.height,
                "player window bottom edge stays above Windows taskbar");
        require(fitted.width >= minimum.width && fitted.height >= minimum.height,
                "player window preserves normal minimum size when screen allows it");

        java.awt.Rectangle smallWork = new java.awt.Rectangle(100, 50, 640, 360);
        java.awt.Rectangle small = EmbeddedPlayerWindow.fitBoundsToWorkArea(
                new java.awt.Rectangle(0, 0, 1320, 820), smallWork, minimum);
        require(small.equals(smallWork),
                "small screens clamp player to the complete usable work area");
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
