package dev.streamflix.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.regex.Pattern;

final class AppLog {
    private static final long MAX_BYTES = 2L * 1024L * 1024L;
    private static final Object LOCK = new Object();
    private static final Pattern API_KEY = Pattern.compile(
            "(?i)(api_key=)[^&\\s]+");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)(Bearer\\s+)[A-Za-z0-9._~+\\-/=]+");

    private AppLog() {}

    static void info(String area, String message) {
        write("INFO", area, message, null);
    }

    static void warn(String area, String message) {
        write("WARN", area, message, null);
    }

    static void warn(String area, String message, Throwable error) {
        write("WARN", area, message, error);
    }

    static void error(String area, String message, Throwable error) {
        write("ERROR", area, message, error);
    }
    static Path logFile() {
        String override = System.getProperty("streamflix.log.dir");
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize().resolve("streamflix.log");
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Local")
                : Path.of(localAppData);
        return base.resolve("Streamflix").resolve("logs").resolve("streamflix.log")
                .toAbsolutePath().normalize();
    }

    private static void write(String level, String area, String message, Throwable error) {
        try {
            synchronized (LOCK) {
                Path file = logFile();
                Files.createDirectories(file.getParent());
                rotateIfNeeded(file);

                StringBuilder line = new StringBuilder()
                        .append(Instant.now()).append(' ')
                        .append(level).append(' ')
                        .append('[').append(clean(area)).append("] ")
                        .append(redact(clean(message)));

                if (error != null) {
                    line.append(" | ")
                            .append(error.getClass().getSimpleName())
                            .append(": ")
                            .append(redact(clean(error.getMessage())));
                }
                line.append(System.lineSeparator());
                Files.writeString(file, line.toString(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Exception ignored) {
            // Logging must never become an application failure.
        }
    }

    private static void rotateIfNeeded(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) < MAX_BYTES) return;
        Path previous = file.resolveSibling("streamflix.log.1");
        Files.deleteIfExists(previous);
        Files.move(file, previous, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return "-";
        return value.replace('\r', ' ').replace('\n', ' ').strip();
    }

    private static String redact(String value) {
        String safe = API_KEY.matcher(value).replaceAll("$1<redacted>");
        return BEARER.matcher(safe).replaceAll("$1<redacted>");
    }
}
