package dev.streamflix.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class UserData {
    private static final Path DATA_DIR;
    private static final Path FAVORITES_FILE;
    private static final Path HISTORY_FILE;

    private static final List<Models.ShowItem> favorites = new CopyOnWriteArrayList<>();
    private static final List<HistoryEntry> history = new CopyOnWriteArrayList<>();

    public record HistoryEntry(Models.ShowItem show, long timestamp, double progressSeconds, double durationSeconds) {}

    static {
        String appData = System.getenv("APPDATA");
        if (appData == null) {
            appData = System.getProperty("user.home") + "/AppData/Roaming";
        }
        DATA_DIR = Path.of(appData, "Streamflix");
        FAVORITES_FILE = DATA_DIR.resolve("favorites.json");
        HISTORY_FILE = DATA_DIR.resolve("history.json");
        loadFavorites();
        loadHistory();
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
            Files.createDirectories(DATA_DIR);
            List<Object> out = new ArrayList<>();
            for (Models.ShowItem item : favorites) {
                out.add(serializeShowItem(item));
            }
            Files.writeString(FAVORITES_FILE, Json.stringify(out), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private static void saveHistory() {
        try {
            Files.createDirectories(DATA_DIR);
            List<Object> out = new ArrayList<>();
            for (HistoryEntry entry : history) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("show", serializeShowItem(entry.show()));
                map.put("timestamp", entry.timestamp());
                map.put("progress", entry.progressSeconds());
                map.put("duration", entry.durationSeconds());
                out.add(map);
            }
            Files.writeString(HISTORY_FILE, Json.stringify(out), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    public static boolean isFavorite(String providerId, String id) {
        return favorites.stream().anyMatch(f -> f.providerId().equals(providerId) && f.id().equals(id));
    }

    public static void toggleFavorite(Models.ShowItem item) {
        if (isFavorite(item.providerId(), item.id())) {
            favorites.removeIf(f -> f.providerId().equals(item.providerId()) && f.id().equals(item.id()));
        } else {
            favorites.add(0, item);
        }
        saveFavorites();
    }

    public static void recordHistory(Models.ShowItem item) {
        recordHistory(item, 0.0, 0.0);
    }

    public static void recordHistory(Models.ShowItem item, double progress, double duration) {
        HistoryEntry existing = history.stream()
                .filter(h -> h.show().providerId().equals(item.providerId()) && h.show().id().equals(item.id()))
                .findFirst().orElse(null);

        // rules: start future playback from the stored position only when progress is meaningful
        // (> 30s and not near the end, e.g. 90%)
        if (existing != null && progress == 0.0 && duration == 0.0) {
            progress = existing.progressSeconds();
            duration = existing.durationSeconds();
        }

        history.removeIf(h -> h.show().providerId().equals(item.providerId()) && h.show().id().equals(item.id()));
        history.add(0, new HistoryEntry(item, System.currentTimeMillis(), progress, duration));
        while (history.size() > 100) {
            history.remove(history.size() - 1);
        }
        saveHistory();
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
            type
        );
    }

    // Methods for testing only
    static void clearForTests() {
        favorites.clear();
        history.clear();
        try {
            Files.deleteIfExists(FAVORITES_FILE);
            Files.deleteIfExists(HISTORY_FILE);
        } catch (Exception ignored) {}
    }

    static void loadForTests() {
        favorites.clear();
        history.clear();
        loadFavorites();
        loadHistory();
    }

    static double getProgressForTest(Models.ShowItem item) {
        HistoryEntry existing = history.stream()
                .filter(h -> h.show().providerId().equals(item.providerId()) && h.show().id().equals(item.id()))
                .findFirst().orElse(null);
        return existing != null ? existing.progressSeconds() : 0.0;
    }
}
