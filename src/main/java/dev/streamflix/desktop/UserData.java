package dev.streamflix.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class UserData {
    private static final String DATA_DIR_PROPERTY = "streamflix.data.dir";
    private static final Path DATA_DIR;
    private static final Path FAVORITES_FILE;
    private static final Path HISTORY_FILE;

    private static final List<Models.ShowItem> favorites = new CopyOnWriteArrayList<>();
    private static final List<HistoryEntry> history = new CopyOnWriteArrayList<>();

    public record HistoryEntry(Models.ShowItem show, long timestamp, double progressSeconds, double durationSeconds) {}

    static {
        DATA_DIR = resolveDataDir();
        FAVORITES_FILE = DATA_DIR.resolve("favorites.json");
        HISTORY_FILE = DATA_DIR.resolve("history.json");
        loadFavorites();
        loadHistory();
    }

    private static Path resolveDataDir() {
        String override = System.getProperty(DATA_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            appData = System.getProperty("user.home") + "/AppData/Roaming";
        }
        return Path.of(appData, "Streamflix").toAbsolutePath().normalize();
    }

    private static void loadFavorites() {
        try {
            if (Files.exists(FAVORITES_FILE)) {
                String content = Files.readString(FAVORITES_FILE, StandardCharsets.UTF_8);
                List<Object> list = Json.array(Json.parse(content));
                for (Object o : list) {
                    Models.ShowItem item = deserializeShowItem(Json.object(o));
                    if (item != null) favorites.add(item);
                }
            }
        } catch (Exception ignored) { }
    }

    private static void loadHistory() {
        try {
            if (Files.exists(HISTORY_FILE)) {
                String content = Files.readString(HISTORY_FILE, StandardCharsets.UTF_8);
                List<Object> list = Json.array(Json.parse(content));
                for (Object o : list) {
                    Map<String, Object> map = Json.object(o);
                    Models.ShowItem item = deserializeShowItem(Json.object(map.get("show")));
                    long timestamp = Json.integer(map.get("timestamp")) != null ? Json.integer(map.get("timestamp")).longValue() : 0L;
                    double progress = Json.decimal(map.get("progress")) != null ? Json.decimal(map.get("progress")) : 0.0;
                    double duration = Json.decimal(map.get("duration")) != null ? Json.decimal(map.get("duration")) : 0.0;
                    if (item != null) {
                        history.add(new HistoryEntry(item, timestamp, progress, duration));
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    private static void saveFavorites() {
        try {
            List<Object> out = new ArrayList<>();
            for (Models.ShowItem item : favorites) {
                out.add(serializeShowItem(item));
            }
            writeAtomically(FAVORITES_FILE, Json.stringify(out));
        } catch (Exception ignored) {}
    }

    private static void saveHistory() {
        try {
            List<Object> out = new ArrayList<>();
            for (HistoryEntry entry : history) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("show", serializeShowItem(entry.show()));
                map.put("timestamp", entry.timestamp());
                map.put("progress", entry.progressSeconds());
                map.put("duration", entry.durationSeconds());
                out.add(map);
            }
            writeAtomically(HISTORY_FILE, Json.stringify(out));
        } catch (Exception ignored) {}
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Files.createDirectories(DATA_DIR);
        Path temp = Files.createTempFile(DATA_DIR, target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static boolean isFavorite(String sourceProviderId, String id) {
        return favorites.stream().anyMatch(f -> sameSourceItem(f, sourceProviderId, id));
    }

    public static void toggleFavorite(String sourceProviderId, Models.ShowItem item) {
        Objects.requireNonNull(sourceProviderId, "sourceProviderId");
        Models.ShowItem stored = item.withSourceProviderId(sourceProviderId);
        if (isFavorite(sourceProviderId, item.id())) {
            favorites.removeIf(f -> sameSourceItem(f, sourceProviderId, item.id()));
        } else {
            favorites.add(0, stored);
        }
        saveFavorites();
    }

    public static void recordHistory(String sourceProviderId, Models.ShowItem item) {
        recordHistory(sourceProviderId, item, 0.0, 0.0);
    }

    public static void recordHistory(String sourceProviderId, Models.ShowItem item, double progress, double duration) {
        Objects.requireNonNull(sourceProviderId, "sourceProviderId");
        Models.ShowItem stored = item.withSourceProviderId(sourceProviderId);
        HistoryEntry existing = history.stream()
                .filter(h -> sameSourceItem(h.show(), sourceProviderId, item.id()))
                .findFirst().orElse(null);

        if (existing != null && progress == 0.0 && duration == 0.0) {
            progress = existing.progressSeconds();
            duration = existing.durationSeconds();
        }

        history.removeIf(h -> sameSourceItem(h.show(), sourceProviderId, item.id()));
        history.add(0, new HistoryEntry(stored, System.currentTimeMillis(), progress, duration));
        while (history.size() > 100) {
            history.remove(history.size() - 1);
        }
        saveHistory();
    }

    private static boolean sameSourceItem(Models.ShowItem item, String sourceProviderId, String id) {
        return Objects.equals(item.sourceProviderId(), sourceProviderId) && Objects.equals(item.id(), id);
    }

    public static List<Models.ShowItem> getFavorites() {
        return new ArrayList<>(favorites);
    }

    public static List<Models.ShowItem> getHistory() {
        return history.stream().map(HistoryEntry::show).collect(Collectors.toList());
    }

    private static Map<String, Object> serializeShowItem(Models.ShowItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.id());
        map.put("providerId", item.providerId());
        if (item.title() != null) map.put("title", item.title());
        if (item.overview() != null) map.put("overview", item.overview());
        if (item.released() != null) map.put("released", item.released());
        if (item.runtimeMinutes() != null) map.put("runtimeMinutes", item.runtimeMinutes());
        if (item.rating() != null) map.put("rating", item.rating());
        if (item.poster() != null) map.put("poster", item.poster());
        if (item.banner() != null) map.put("banner", item.banner());
        if (item.type() != null) map.put("type", item.type().name());
        if (item.sourceProviderId() != null && !item.sourceProviderId().isBlank()) {
            map.put("sourceProviderId", item.sourceProviderId());
        }
        return map;
    }

    private static Models.ShowItem deserializeShowItem(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        String id = Json.string(map.get("id"));
        String providerId = Json.string(map.get("providerId"));
        if (id == null || id.isEmpty() || providerId == null || providerId.isEmpty()) return null;

        Models.ShowType type = null;
        String typeStr = Json.string(map.get("type"));
        if (typeStr != null && !typeStr.isEmpty()) {
            try { type = Models.ShowType.valueOf(typeStr); } catch (Exception ignored) {}
        }

        return new Models.ShowItem(
            id,
            providerId,
            Json.string(map.get("title")).isEmpty() ? null : Json.string(map.get("title")),
            Json.string(map.get("overview")).isEmpty() ? null : Json.string(map.get("overview")),
            Json.string(map.get("released")).isEmpty() ? null : Json.string(map.get("released")),
            Json.integer(map.get("runtimeMinutes")),
            Json.decimal(map.get("rating")),
            Json.string(map.get("poster")).isEmpty() ? null : Json.string(map.get("poster")),
            Json.string(map.get("banner")).isEmpty() ? null : Json.string(map.get("banner")),
            type,
            Json.string(map.get("sourceProviderId")).isEmpty() ? null : Json.string(map.get("sourceProviderId"))
        );
    }

    // Methods for testing only. Tests must opt into an isolated directory before this class loads.
    static void clearForTests() {
        requireTestDataDir();
        favorites.clear();
        history.clear();
        try {
            Files.deleteIfExists(FAVORITES_FILE);
            Files.deleteIfExists(HISTORY_FILE);
        } catch (Exception ignored) {}
    }

    private static void requireTestDataDir() {
        String override = System.getProperty(DATA_DIR_PROPERTY);
        if (override == null || override.isBlank()) {
            throw new IllegalStateException("Tests must set -D" + DATA_DIR_PROPERTY + " to an isolated temporary directory before UserData is loaded.");
        }
        Path requested = Path.of(override).toAbsolutePath().normalize();
        if (!requested.equals(DATA_DIR)) {
            throw new IllegalStateException("Test data directory was changed after UserData initialization.");
        }
    }

    static void loadForTests() {
        favorites.clear();
        history.clear();
        loadFavorites();
        loadHistory();
    }

    static double getProgressForTest(String sourceProviderId, Models.ShowItem item) {
        HistoryEntry existing = history.stream()
                .filter(h -> sameSourceItem(h.show(), sourceProviderId, item.id()))
                .findFirst().orElse(null);
        return existing != null ? existing.progressSeconds() : 0.0;
    }
}
