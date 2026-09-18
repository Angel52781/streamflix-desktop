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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class UserData {
    private static final String DATA_DIR_PROPERTY = "streamflix.data.dir";
    private static final Path DATA_DIR;
    private static final Path FAVORITES_FILE;
    private static final Path HISTORY_FILE;

    private static final List<Models.ShowItem> favorites = new CopyOnWriteArrayList<>();
    private static final List<HistoryEntry> history = new CopyOnWriteArrayList<>();
    private static final ExecutorService HISTORY_WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "streamflix-history-writer");
        thread.setDaemon(true);
        return thread;
    });

    public record HistoryEntry(
            Models.ShowItem show,
            long timestamp,
            double progressSeconds,
            double durationSeconds,
            String mediaId,
            Integer seasonNumber,
            Integer episodeNumber,
            String mediaTitle
    ) {}

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
        } catch (Exception ex) {
            AppLog.warn("userdata", "No se pudieron cargar favoritos.", ex);
        }
    }

    private static void loadHistory() {
        try {
            if (Files.exists(HISTORY_FILE)) {
                String content = Files.readString(HISTORY_FILE, StandardCharsets.UTF_8);
                List<Object> list = Json.array(Json.parse(content));
                for (Object o : list) {
                    Map<String, Object> map = Json.object(o);
                    Models.ShowItem item = deserializeShowItem(Json.object(map.get("show")));
                    Long timestampValue = longNumber(map.get("timestamp"));
                    long timestamp = timestampValue != null ? timestampValue : 0L;
                    double progress = Json.decimal(map.get("progress")) != null ? Json.decimal(map.get("progress")) : 0.0;
                    double duration = Json.decimal(map.get("duration")) != null ? Json.decimal(map.get("duration")) : 0.0;
                    String mediaId = Json.string(map.get("mediaId"));
                    Integer seasonNumber = Json.integer(map.get("seasonNumber"));
                    Integer episodeNumber = Json.integer(map.get("episodeNumber"));
                    String mediaTitle = Json.string(map.get("mediaTitle"));
                    if (item != null) {
                        history.add(new HistoryEntry(
                                item,
                                timestamp,
                                progress,
                                duration,
                                mediaId.isBlank() ? null : mediaId,
                                seasonNumber,
                                episodeNumber,
                                mediaTitle.isBlank() ? null : mediaTitle
                        ));
                    }
                }
            }
        } catch (Exception ex) {
            AppLog.warn("userdata", "No se pudo cargar el historial.", ex);
        }
    }

    private static void saveFavorites() {
        try {
            List<Object> out = new ArrayList<>();
            for (Models.ShowItem item : favorites) {
                out.add(serializeShowItem(item));
            }
            writeAtomically(FAVORITES_FILE, Json.stringify(out));
        } catch (Exception ex) {
            AppLog.warn("userdata", "No se pudieron guardar favoritos.", ex);
        }
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
                if (entry.mediaId() != null) map.put("mediaId", entry.mediaId());
                if (entry.seasonNumber() != null) map.put("seasonNumber", entry.seasonNumber());
                if (entry.episodeNumber() != null) map.put("episodeNumber", entry.episodeNumber());
                if (entry.mediaTitle() != null) map.put("mediaTitle", entry.mediaTitle());
                out.add(map);
            }
            writeAtomically(HISTORY_FILE, Json.stringify(out));
        } catch (Exception ex) {
            AppLog.warn("userdata", "No se pudo guardar el historial.", ex);
        }
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
        recordHistoryInternal(sourceProviderId, item, item.providerId(), null, null, item.title(), progress, duration);
    }

    public static void recordEpisodeHistory(String sourceProviderId, Models.ShowItem show,
                                            Models.Episode episode, double progress, double duration) {
        Objects.requireNonNull(episode, "episode");
        recordHistoryInternal(
                sourceProviderId,
                show,
                episode.id(),
                episode.seasonNumber(),
                episode.episodeNumber(),
                show.title() + " · T" + episode.seasonNumber() + "E" + episode.episodeNumber(),
                progress,
                duration
        );
    }

    static void recordHistoryAsync(String sourceProviderId, Models.ShowItem item,
                                   double progress, double duration) {
        HISTORY_WRITER.execute(() ->
                recordHistoryInternal(sourceProviderId, item, item.providerId(), null, null,
                        item.title(), progress, duration));
    }

    static void recordEpisodeHistoryAsync(String sourceProviderId, Models.ShowItem show,
                                          Models.Episode episode, double progress, double duration) {
        Objects.requireNonNull(episode, "episode");
        HISTORY_WRITER.execute(() ->
                recordHistoryInternal(
                        sourceProviderId,
                        show,
                        episode.id(),
                        episode.seasonNumber(),
                        episode.episodeNumber(),
                        show.title() + " · T" + episode.seasonNumber() + "E" + episode.episodeNumber(),
                        progress,
                        duration
                ));
    }

    static void recordHistoryFinal(String sourceProviderId, Models.ShowItem item,
                                   double progress, double duration) {
        awaitHistoryWrite(() ->
                recordHistoryInternal(sourceProviderId, item, item.providerId(), null, null,
                        item.title(), progress, duration));
    }

    static void recordEpisodeHistoryFinal(String sourceProviderId, Models.ShowItem show,
                                          Models.Episode episode, double progress, double duration) {
        Objects.requireNonNull(episode, "episode");
        awaitHistoryWrite(() ->
                recordHistoryInternal(
                        sourceProviderId,
                        show,
                        episode.id(),
                        episode.seasonNumber(),
                        episode.episodeNumber(),
                        show.title() + " · T" + episode.seasonNumber() + "E" + episode.episodeNumber(),
                        progress,
                        duration
                ));
    }

    private static void awaitHistoryWrite(Runnable write) {
        try {
            Future<?> future = HISTORY_WRITER.submit(write);
            future.get(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            // Preserve the user's final position even if the background writer is unavailable.
            write.run();
        }
    }

    private static synchronized void recordHistoryInternal(
                                      String sourceProviderId, Models.ShowItem item,
                                      String mediaId, Integer seasonNumber, Integer episodeNumber,
                                      String mediaTitle, double progress, double duration) {
        Objects.requireNonNull(sourceProviderId, "sourceProviderId");
        Models.ShowItem stored = item.withSourceProviderId(sourceProviderId);
        final String lookupMediaId = mediaId;
        final Integer lookupSeasonNumber = seasonNumber;
        final Integer lookupEpisodeNumber = episodeNumber;
        HistoryEntry existing = history.stream()
                .filter(h -> sameHistoryMedia(
                        h, sourceProviderId, item,
                        lookupMediaId, lookupSeasonNumber, lookupEpisodeNumber))
                .findFirst().orElse(null);

        if (existing != null && progress == 0.0 && duration == 0.0) {
            progress = existing.progressSeconds();
            duration = existing.durationSeconds();
            if (mediaId == null) mediaId = existing.mediaId();
            if (seasonNumber == null) seasonNumber = existing.seasonNumber();
            if (episodeNumber == null) episodeNumber = existing.episodeNumber();
            if (mediaTitle == null) mediaTitle = existing.mediaTitle();
        }

        final String finalMediaId = mediaId;
        final Integer finalSeasonNumber = seasonNumber;
        final Integer finalEpisodeNumber = episodeNumber;
        history.removeIf(h -> sameHistoryMedia(
                h, sourceProviderId, item,
                finalMediaId, finalSeasonNumber, finalEpisodeNumber));
        history.add(0, new HistoryEntry(
                stored,
                System.currentTimeMillis(),
                progress,
                duration,
                mediaId,
                seasonNumber,
                episodeNumber,
                mediaTitle
        ));
        // Episode-level history needs more room than the old one-entry-per-show model.
        while (history.size() > 300) {
            history.remove(history.size() - 1);
        }
        saveHistory();
    }

    private static boolean sameSourceItem(Models.ShowItem item, String sourceProviderId, String id) {
        return Objects.equals(item.sourceProviderId(), sourceProviderId) && Objects.equals(item.id(), id);
    }

    private static String historyShowKey(Models.ShowItem item, String sourceProviderId) {
        if (item == null) return "";
        String id = item.id() == null ? "" : item.id();
        // TMDb EN and ES are two metadata views of the same canonical title.
        if (id.startsWith("tmdb:movie:") || id.startsWith("tmdb:tv:")) return id;
        String source = sourceProviderId;
        if ((source == null || source.isBlank()) && item.sourceProviderId() != null) {
            source = item.sourceProviderId();
        }
        return (source == null ? "" : source) + "\u0000" + id;
    }

    private static boolean sameHistoryShow(
            HistoryEntry entry, String sourceProviderId, Models.ShowItem item) {
        return Objects.equals(
                historyShowKey(entry.show(), entry.show().sourceProviderId()),
                historyShowKey(item, sourceProviderId));
    }

    private static boolean sameHistoryMedia(
            HistoryEntry entry, String sourceProviderId, Models.ShowItem item,
            String mediaId, Integer seasonNumber, Integer episodeNumber) {
        if (!sameHistoryShow(entry, sourceProviderId, item)) return false;
        if (seasonNumber != null && episodeNumber != null) {
            return Objects.equals(entry.seasonNumber(), seasonNumber)
                    && Objects.equals(entry.episodeNumber(), episodeNumber);
        }
        if (mediaId != null && entry.mediaId() != null) {
            return Objects.equals(entry.mediaId(), mediaId);
        }
        return entry.seasonNumber() == null && entry.episodeNumber() == null;
    }

    private static HistoryEntry selectContinueEntry(List<HistoryEntry> entries) {
        HistoryEntry best = null;
        for (HistoryEntry candidate : entries) {
            if (best == null || compareContinue(candidate, best) > 0) best = candidate;
        }
        return best;
    }

    private static int compareContinue(HistoryEntry a, HistoryEntry b) {
        boolean aEpisode = a.seasonNumber() != null && a.episodeNumber() != null;
        boolean bEpisode = b.seasonNumber() != null && b.episodeNumber() != null;
        if (aEpisode != bEpisode) return aEpisode ? 1 : -1;
        if (aEpisode) {
            int season = Integer.compare(a.seasonNumber(), b.seasonNumber());
            if (season != 0) return season;
            int episode = Integer.compare(a.episodeNumber(), b.episodeNumber());
            if (episode != 0) return episode;
        }
        return Long.compare(a.timestamp(), b.timestamp());
    }

    private static List<HistoryEntry> continueWatchingEntries() {
        Map<String, List<HistoryEntry>> grouped = new LinkedHashMap<>();
        for (HistoryEntry entry : history) {
            String key = historyShowKey(entry.show(), entry.show().sourceProviderId());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        }
        List<HistoryEntry> out = new ArrayList<>();
        for (List<HistoryEntry> group : grouped.values()) {
            HistoryEntry selected = selectContinueEntry(group);
            if (!isContinueWatchingCandidate(selected)) continue;

            Models.ShowItem displayShow = preferredContinueShow(group, selected.show());
            out.add(new HistoryEntry(
                    displayShow,
                    selected.timestamp(),
                    selected.progressSeconds(),
                    selected.durationSeconds(),
                    selected.mediaId(),
                    selected.seasonNumber(),
                    selected.episodeNumber(),
                    selected.mediaTitle()
            ));
        }
        return out;
    }

    private static Models.ShowItem preferredContinueShow(
            List<HistoryEntry> group, Models.ShowItem fallback) {
        if (fallback == null || fallback.id() == null
                || !(fallback.id().startsWith("tmdb:movie:")
                || fallback.id().startsWith("tmdb:tv:"))) {
            return fallback;
        }

        String preferredSource = "tmdb-" + TmdbSettings.catalogLanguage();
        return group.stream()
                .map(HistoryEntry::show)
                .filter(Objects::nonNull)
                .filter(show -> preferredSource.equals(show.sourceProviderId()))
                .findFirst()
                .orElse(fallback);
    }

    private static boolean isContinueWatchingCandidate(HistoryEntry entry) {
        if (entry == null || entry.show() == null) return false;
        String source = entry.show().sourceProviderId();
        if (source == null || source.isBlank()) return false;
        if (entry.durationSeconds() <= 0 || entry.progressSeconds() <= 1.0) return false;
        return entry.progressSeconds() < entry.durationSeconds() * 0.95;
    }

    public static List<Models.ShowItem> getFavorites() {
        return new ArrayList<>(favorites);
    }

    public static void clearFavorites() {
        favorites.clear();
        saveFavorites();
    }

    public static void clearHistory() {
        awaitHistoryWrite(() -> {
            history.clear();
            saveHistory();
        });
    }

    public static List<Models.ShowItem> getHistory() {
        return continueWatchingEntries().stream()
                .map(HistoryEntry::show)
                .collect(Collectors.toList());
    }

    public static List<HistoryEntry> getHistoryEntries() {
        return new ArrayList<>(history);
    }

    public static HistoryEntry getHistoryEntry(String sourceProviderId, Models.ShowItem item) {
        if (sourceProviderId == null || item == null) return null;
        List<HistoryEntry> matching = history.stream()
                .filter(h -> sameHistoryShow(h, sourceProviderId, item))
                .toList();
        return selectContinueEntry(matching);
    }

    public static HistoryEntry getEpisodeHistoryEntry(
            String sourceProviderId, Models.ShowItem item, Models.Episode episode) {
        if (sourceProviderId == null || item == null || episode == null) return null;
        return history.stream()
                .filter(h -> sameHistoryMedia(
                        h, sourceProviderId, item, episode.id(),
                        episode.seasonNumber(), episode.episodeNumber()))
                .findFirst().orElse(null);
    }

    public static double progressFraction(String sourceProviderId, Models.ShowItem item) {
        HistoryEntry entry = getHistoryEntry(sourceProviderId, item);
        if (entry == null || entry.durationSeconds() <= 0) return 0.0;
        double fraction = entry.progressSeconds() / entry.durationSeconds();
        if (!Double.isFinite(fraction)) return 0.0;
        return Math.max(0.0, Math.min(1.0, fraction));
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

    private static Long longNumber(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.valueOf(Json.string(value)); }
        catch (Exception ignored) { return null; }
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
        HistoryEntry existing = getHistoryEntry(sourceProviderId, item);
        return existing != null ? existing.progressSeconds() : 0.0;
    }
}
