package dev.streamflix.desktop;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class MpvPlayer {
    private MpvPlayer() {}

    static boolean isAvailable() { return locate() != null; }

    static void play(Models.Video video, String title) throws Exception {
        String mpv = requireMpv();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(mpv);
        cmd.add("--force-window=yes");
        cmd.add("--keep-open=no");
        cmd.add("--save-position-on-quit");
        cmd.add("--hwdec=auto-safe");
        cmd.add("--force-media-title=" + safe(title));
        addHeaders(cmd, video);
        for (Models.Subtitle subtitle : video.subtitles()) {
            cmd.add("--sub-file=" + subtitle.file());
        }
        cmd.add(video.source());
        new ProcessBuilder(cmd).redirectErrorStream(true).start();
    }

    static int smoke(Models.Video video) throws Exception {
        String mpv = requireMpv();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(mpv);
        cmd.add("--no-config");
        cmd.add("--vo=null");
        cmd.add("--ao=null");
        cmd.add("--frames=1");
        cmd.add("--network-timeout=10");
        cmd.add("--really-quiet");
        addHeaders(cmd, video);
        cmd.add(video.source());

        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        Thread drain = new Thread(() -> {
            try { process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream()); }
            catch (Exception ignored) {}
        }, "mpv-smoke-drain");
        drain.setDaemon(true);
        drain.start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            return 124;
        }
        return process.exitValue();
    }

    private static void addHeaders(List<String> cmd, Models.Video video) {
        if (video.headers() == null || video.headers().isEmpty()) return;
        String headers = video.headers().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue().replace(",", "\\,"))
                .reduce((a, b) -> a + "," + b).orElse("");
        if (!headers.isBlank()) cmd.add("--http-header-fields=" + headers);
    }

    private static String requireMpv() {
        String mpv = locate();
        if (mpv == null) throw new IllegalStateException("mpv no fue encontrado.");
        return mpv;
    }

    private static String locate() {
        String env = System.getenv("STREAMFLIX_MPV");
        if (isExecutable(env)) return env;

        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            Path appDir = Path.of(appPath).toAbsolutePath().getParent();
            if (appDir != null) {
                Path bundled = appDir.resolve("tools").resolve("mpv").resolve(isWindows() ? "mpv.exe" : "mpv");
                if (Files.isRegularFile(bundled)) return bundled.toString();
            }
        }
        List<String> candidates = List.of(
                "tools" + File.separator + "mpv" + File.separator + (isWindows() ? "mpv.exe" : "mpv"),
                "C:\\Program Files\\mpv\\mpv.exe",
                "C:\\Program Files (x86)\\mpv\\mpv.exe"
        );
        for (String candidate : candidates) {
            if (isExecutable(candidate)) return candidate;
        }

        String path = System.getenv("PATH");
        if (path != null) {
            String filename = isWindows() ? "mpv.exe" : "mpv";
            for (String dir : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                Path candidate = Path.of(dir, filename);
                if (Files.isRegularFile(candidate)) return candidate.toString();
            }
        }
        return null;
    }

    private static boolean isExecutable(String path) {
        if (path == null || path.isBlank()) return false;
        try { return Files.isRegularFile(Path.of(path)); }
        catch (Exception ignored) { return false; }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String safe(String value) {
        return value == null ? "Streamflix" : value.replace('\n', ' ').replace('\r', ' ');
    }
}
