package dev.streamflix.desktop;

import java.io.File;
import java.nio.file.*;
import java.util.*;

final class MpvPlayer {
    private MpvPlayer() {}

    static boolean isAvailable() { return locate() != null; }

    static void play(Models.Video video, String title) throws Exception {
        String mpv = locate();
        if (mpv == null) throw new IllegalStateException(
                "mpv no fue encontrado. Instálalo, pon mpv.exe en tools\\mpv o define STREAMFLIX_MPV."
        );
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(mpv);
        cmd.add("--force-window=yes");
        cmd.add("--keep-open=no");
        cmd.add("--save-position-on-quit");
        cmd.add("--hwdec=auto-safe");
        cmd.add("--force-media-title=" + safe(title));
        if (video.headers() != null && !video.headers().isEmpty()) {
            String headers = video.headers().entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue().replace(",", "\\,"))
                    .reduce((a, b) -> a + "," + b).orElse("");
            if (!headers.isBlank()) cmd.add("--http-header-fields=" + headers);
        }
        for (Models.Subtitle subtitle : video.subtitles()) {
            cmd.add("--sub-file=" + subtitle.file());
        }
        cmd.add(video.source());
        new ProcessBuilder(cmd).redirectErrorStream(true).start();
    }

    private static String locate() {
        String env = System.getenv("STREAMFLIX_MPV");
        if (isExecutable(env)) return env;
        List<String> candidates = List.of(
                "tools" + File.separator + "mpv" + File.separator + (isWindows() ? "mpv.exe" : "mpv"),
                "C:\\Program Files\\mpv\\mpv.exe",
                "C:\\Program Files (x86)\\mpv\\mpv.exe"
        );
        for (String c : candidates) if (isExecutable(c)) return c;
        String path = System.getenv("PATH");
        if (path != null) {
            String filename = isWindows() ? "mpv.exe" : "mpv";
            for (String dir : path.split(Patterns.pathSeparatorRegex())) {
                Path candidate = Path.of(dir, filename);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return candidate.toString();
            }
        }
        return null;
    }

    private static boolean isExecutable(String path) {
        if (path == null || path.isBlank()) return false;
        try { return Files.isRegularFile(Path.of(path)); }
        catch (Exception ignored) { return false; }
    }
    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }
    private static String safe(String s) { return s == null ? "Streamflix" : s.replace('\n', ' ').replace('\r', ' '); }

    private static final class Patterns {
        static String pathSeparatorRegex() { return java.util.regex.Pattern.quote(File.pathSeparator); }
    }
}
