package dev.streamflix.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;

/** Read-only local configuration; no key is bundled or persisted by this class. */
final class TmdbSettings {
    private TmdbSettings() {}

    static String apiKey() throws TmdbException {
        return apiKey(System.getenv(), System.getProperty("user.home"));
    }

    // Explicit inputs keep tests independent of the user's credentials and settings.
    static String apiKey(Map<String, String> environment, String userHome) throws TmdbException {
        String key = environment.get("STREAMFLIX_TMDB_API_KEY");
        if (key != null && !key.isBlank()) return key.strip();
        try {
            Path file = settingsFile(environment, userHome);
            Object parsed = Json.parse(Files.readString(file));
            if (!(parsed instanceof Map<?, ?> settings)) throw new IllegalArgumentException();
            Object value = settings.get("tmdbApiKey");
            if (value == null) return "";
            if (!(value instanceof String text)) throw new IllegalArgumentException();
            return text.strip();
        } catch (NoSuchFileException e) {
            return "";
        } catch (IOException | RuntimeException e) {
            // JSON parser and filesystem errors may contain file contents or private paths.
            throw new TmdbException("Cannot read local settings. Check Streamflix/settings.json and its tmdbApiKey field.");
        }
    }

    static Path settingsFile(Map<String, String> environment, String userHome) {
        String appData = environment.get("APPDATA");
        Path base = appData == null || appData.isBlank()
                ? Path.of(userHome, "AppData", "Roaming") : Path.of(appData);
        return base.resolve("Streamflix").resolve("settings.json");
    }
}
