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

/** Local TMDb configuration. Environment configuration always takes precedence. */
final class TmdbSettings {
    private static final String DATA_DIR_PROPERTY = "streamflix.data.dir";
    private static final String ENV_KEY = "STREAMFLIX_TMDB_API_KEY";

    private TmdbSettings() {}

    static String apiKey() throws TmdbException {
        String environmentKey = clean(System.getenv(ENV_KEY));
        if (!environmentKey.isBlank()) return environmentKey;
        return readApiKey(settingsFile());
    }

    // Explicit inputs keep fixtures independent of the user's credentials and settings.
    static String apiKey(Map<String, String> environment, String userHome) throws TmdbException {
        String environmentKey = clean(environment.get(ENV_KEY));
        if (!environmentKey.isBlank()) return environmentKey;
        return readApiKey(settingsFile(environment, userHome));
    }

    static boolean environmentOverrideActive() {
        return !clean(System.getenv(ENV_KEY)).isBlank();
    }

    static boolean hasApiKey() throws TmdbException {
        return !apiKey().isBlank();
    }

    static String localApiKey() throws TmdbException {
        return readApiKey(settingsFile());
    }

    static void saveApiKey(String value) throws TmdbException {
        String key = clean(value);
        validateForSave(key);
        Path file = settingsFile();
        Map<String, Object> settings = readSettingsForUpdate(file);
        if (key.isBlank()) settings.remove("tmdbApiKey");
        else settings.put("tmdbApiKey", key);
        writeAtomically(file, Json.stringify(settings));
    }

    static Path settingsFile(Map<String, String> environment, String userHome) {
        String appData = environment.get("APPDATA");
        Path base = appData == null || appData.isBlank()
                ? Path.of(userHome, "AppData", "Roaming") : Path.of(appData);
        return base.resolve("Streamflix").resolve("settings.json");
    }

    private static Path settingsFile() {
        String override = System.getProperty(DATA_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize().resolve("settings.json");
        }
        return settingsFile(System.getenv(), System.getProperty("user.home")).toAbsolutePath().normalize();
    }

    private static String readApiKey(Path file) throws TmdbException {
        try {
            if (!Files.exists(file)) return "";
            Map<String, Object> settings = parseSettings(Files.readString(file, StandardCharsets.UTF_8));
            Object value = settings.get("tmdbApiKey");
            if (value == null) return "";
            if (!(value instanceof String text)) throw new IllegalArgumentException();
            return text.strip();
        } catch (NoSuchFileException e) {
            return "";
        } catch (IOException | RuntimeException e) {
            throw new TmdbException("Cannot read local settings. Check Streamflix/settings.json and its tmdbApiKey field.");
        }
    }

    private static Map<String, Object> readSettingsForUpdate(Path file) throws TmdbException {
        try {
            if (!Files.exists(file)) return new LinkedHashMap<>();
            return new LinkedHashMap<>(parseSettings(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (NoSuchFileException e) {
            return new LinkedHashMap<>();
        } catch (IOException | RuntimeException e) {
            throw new TmdbException("Cannot update local settings because settings.json is unreadable or invalid.");
        }
    }

    private static Map<String, Object> parseSettings(String json) {
        Object parsed = Json.parse(json);
        if (!(parsed instanceof Map<?, ?>)) throw new IllegalArgumentException("settings.json must contain an object");
        return Json.object(parsed);
    }

    private static void validateForSave(String key) throws TmdbException {
        if (key.isBlank()) return; // Empty means remove the local key.
        if (key.length() < 16 || key.length() > 512) {
            throw new TmdbException("TMDb credential must be between 16 and 512 characters.");
        }
        if (key.chars().anyMatch(Character::isWhitespace) || key.chars().anyMatch(Character::isISOControl)) {
            throw new TmdbException("TMDb credential cannot contain whitespace or control characters.");
        }
    }

    private static void writeAtomically(Path target, String content) throws TmdbException {
        Path dir = target.getParent();
        Path temp = null;
        try {
            Files.createDirectories(dir);
            temp = Files.createTempFile(dir, "settings-", ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new TmdbException("Cannot save local TMDb settings.");
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            }
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
