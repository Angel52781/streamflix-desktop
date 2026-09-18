package dev.streamflix.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Non-secret local playback and window preferences stored beside TMDb settings. */
final class PlaybackSettings {
    private static final String DATA_DIR_PROPERTY = "streamflix.data.dir";

    private PlaybackSettings() {}

    static boolean startMaximized() {
        Object raw = read().get("startMaximized");
        return !(raw instanceof Boolean value) || value;
    }

    static String audioLanguage() {
        return language(read().get("audioLanguage"), "auto", true);
    }

    static String subtitleLanguage() {
        return language(read().get("subtitleLanguage"), "es", false);
    }

    static String qualityProfile() {
        return quality(read().get("qualityProfile"));
    }

    static void save(boolean startMaximized, String audioLanguage,
                     String subtitleLanguage, String qualityProfile) {
        Map<String, Object> settings = new LinkedHashMap<>(read());
        settings.put("startMaximized", startMaximized);
        settings.put("audioLanguage", language(audioLanguage, "auto", true));
        settings.put("subtitleLanguage", language(subtitleLanguage, "es", false));
        settings.put("qualityProfile", quality(qualityProfile));
        write(settings);
    }

    static void saveQualityProfile(String qualityProfile) {
        Map<String, Object> settings = new LinkedHashMap<>(read());
        settings.put("qualityProfile", quality(qualityProfile));
        write(settings);
    }

    static String audioPreferenceArgument() {
        return switch (audioLanguage()) {
            case "es" -> "es,spa,es-ES,es-419,en,eng";
            case "en" -> "en,eng,es,spa,es-ES,es-419";
            default -> "es,spa,es-ES,es-419,en,eng";
        };
    }

    static String subtitlePreferenceArgument() {
        return switch (subtitleLanguage()) {
            case "en" -> "en,eng,es,spa,es-ES,es-419";
            case "off" -> "";
            default -> "es,spa,es-ES,es-419,en,eng";
        };
    }

    static String hlsBitrateArgument() {
        return hlsBitrateArgument(qualityProfile());
    }

    static String hlsBitrateArgument(String profile) {
        return switch (quality(profile)) {
            // Auto deliberately avoids mpv's "max" default. If playback stalls,
            // EmbeddedPlayerWindow can temporarily lower this cap further.
            case "saver" -> "1500000";
            case "balanced" -> "3000000";
            case "high" -> "6000000";
            case "max" -> "max";
            default -> "4000000";
        };
    }

    static String qualityLabel(String profile) {
        return switch (quality(profile)) {
            case "saver" -> "Ahorro";
            case "balanced" -> "Equilibrada";
            case "high" -> "Alta";
            case "max" -> "Máxima";
            default -> "Auto";
        };
    }

    private static String quality(Object raw) {
        String value = raw instanceof String text ? text.strip().toLowerCase() : "";
        return switch (value) {
            case "saver", "balanced", "high", "max" -> value;
            default -> "auto";
        };
    }

    private static String language(Object raw, String fallback, boolean allowAuto) {
        String value = raw instanceof String text ? text.strip().toLowerCase() : "";
        if ("es".equals(value) || "en".equals(value)) return value;
        if (allowAuto && "auto".equals(value)) return value;
        if (!allowAuto && "off".equals(value)) return value;
        return fallback;
    }

    private static Map<String, Object> read() {
        Path file = settingsFile();
        try {
            if (!Files.exists(file)) return new LinkedHashMap<>();
            Object parsed = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?>)) return new LinkedHashMap<>();
            return new LinkedHashMap<>(Json.object(parsed));
        } catch (NoSuchFileException ignored) {
            return new LinkedHashMap<>();
        } catch (IOException | RuntimeException ignored) {
            return new LinkedHashMap<>();
        }
    }

    private static void write(Map<String, Object> settings) {
        Path target = settingsFile();
        Path dir = target.getParent();
        Path temp = null;
        try {
            Files.createDirectories(dir);
            temp = Files.createTempFile(dir, "settings-", ".tmp");
            Files.writeString(temp, Json.stringify(settings), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            }
        }
    }

    private static Path settingsFile() {
        String override = System.getProperty(DATA_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize().resolve("settings.json");
        }

        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Roaming")
                : Path.of(appData);
        return base.resolve("Streamflix").resolve("settings.json").toAbsolutePath().normalize();
    }
}
