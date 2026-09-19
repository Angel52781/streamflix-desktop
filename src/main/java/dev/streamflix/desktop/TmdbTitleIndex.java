package dev.streamflix.desktop;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.zip.GZIPInputStream;

/**
 * Lightweight prefix index backed by TMDb's official daily ID exports.
 *
 * The export is downloaded once per UTC day and cached as newline-delimited JSON.
 * Searches scan the local file in a background worker, so no large catalog needs
 * to stay resident in memory and incremental typing does not hammer the TMDb API.
 */
final class TmdbTitleIndex {
    private static final String CACHE_DIR_PROPERTY = "streamflix.tmdb.index.dir";
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("MM_dd_yyyy");
    private static final long MAX_COMPRESSED_BYTES = 12L * 1024L * 1024L;
    private static final long MAX_DECOMPRESSED_BYTES = 64L * 1024L * 1024L;
    private static final Object DOWNLOAD_LOCK = new Object();

    private TmdbTitleIndex() {}

    record Match(int id, String originalTitle, double popularity, int quality) {}

    static void warmUpTv() {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try { ensureTvIndex(); }
            catch (Exception ex) {
                AppLog.warn("tmdb-index", "No se pudo precargar el índice local de series.", ex);
            }
        });
    }

    static List<Match> searchTv(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) return List.of();
        String normalizedQuery = normalize(query);
        if (normalizedQuery.length() < 3) return List.of();

        try {
            Path index = ensureTvIndex();
            return searchFile(index, normalizedQuery, limit);
        } catch (Exception ex) {
            AppLog.warn("tmdb-index", "No se pudo usar el índice local de series.", ex);
            return List.of();
        }
    }

    static List<Match> searchFile(Path file, String query, int limit) throws IOException {
        String normalizedQuery = normalize(query);
        if (!Files.isRegularFile(file) || normalizedQuery.length() < 3 || limit <= 0) {
            return List.of();
        }

        Comparator<Match> bestFirst = Comparator
                .comparingInt(Match::quality)
                .thenComparing(Comparator.comparingDouble(Match::popularity).reversed())
                .thenComparingInt(m -> normalize(m.originalTitle()).length())
                .thenComparing(Match::originalTitle, String.CASE_INSENSITIVE_ORDER);

        PriorityQueue<Match> top = new PriorityQueue<>(limit, bestFirst.reversed());
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Match match = parseMatch(line, normalizedQuery);
                if (match == null) continue;
                if (top.size() < limit) {
                    top.add(match);
                } else if (bestFirst.compare(match, top.peek()) < 0) {
                    top.poll();
                    top.add(match);
                }
            }
        }

        ArrayList<Match> result = new ArrayList<>(top);
        result.sort(bestFirst);
        return List.copyOf(result);
    }

    private static Match parseMatch(String line, String query) {
        try {
            Object parsed = Json.parse(line);
            if (!(parsed instanceof Map<?, ?>)) return null;
            Map<String, Object> map = Json.object(parsed);
            Integer id = integer(map.get("id"));
            String title = Json.string(map.get("original_name"));
            if (id == null || id <= 0 || title == null || title.isBlank()) return null;

            String normalizedTitle = normalize(title);
            int quality = prefixQuality(normalizedTitle, query);
            if (quality < 0) return null;
            Double popularity = Json.decimal(map.get("popularity"));
            return new Match(id, title.strip(), popularity == null ? 0.0 : popularity, quality);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static int prefixQuality(String normalizedTitle, String normalizedQuery) {
        if (normalizedTitle == null || normalizedQuery == null
                || normalizedQuery.length() < 3) return -1;
        if (normalizedTitle.startsWith(normalizedQuery)) return 0;
        for (String word : normalizedTitle.split(" +")) {
            if (word.startsWith(normalizedQuery)) return 1;
        }
        return -1;
    }

    static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('&', ' ')
                .replaceAll("[^a-z0-9]+", " ")
                .strip()
                .replaceAll(" +", " ");
        return normalized;
    }

    private static Path ensureTvIndex() throws IOException, InterruptedException {
        Path dir = cacheDir();
        Files.createDirectories(dir);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int daysBack = 0; daysBack <= 1; daysBack++) {
            LocalDate date = today.minusDays(daysBack);
            Path target = dir.resolve("tv_series_ids_" + FILE_DATE.format(date) + ".json");
            if (Files.isRegularFile(target) && Files.size(target) > 0) {
                cleanupOldIndexes(dir, target);
                return target;
            }

            synchronized (DOWNLOAD_LOCK) {
                if (Files.isRegularFile(target) && Files.size(target) > 0) return target;
                try {
                    downloadTvIndex(date, target);
                    cleanupOldIndexes(dir, target);
                    return target;
                } catch (IOException ex) {
                    if (daysBack == 1) throw ex;
                }
            }
        }
        throw new IOException("TMDb TV export unavailable");
    }

    private static void downloadTvIndex(LocalDate date, Path target)
            throws IOException, InterruptedException {
        String filename = "tv_series_ids_" + FILE_DATE.format(date) + ".json.gz";
        String url = "https://files.tmdb.org/p/exports/" + filename;

        byte[] compressed = new Http().getBytes(url);
        if (compressed.length == 0 || compressed.length > MAX_COMPRESSED_BYTES) {
            throw new IOException("Unexpected TMDb TV index size");
        }

        Path temp = Files.createTempFile(target.getParent(), "tv-index-", ".part");
        long written = 0L;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
             var out = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = gzip.read(buffer)) >= 0) {
                if (read == 0) continue;
                written += read;
                if (written > MAX_DECOMPRESSED_BYTES) {
                    throw new IOException("TMDb TV index exceeds size limit");
                }
                out.write(buffer, 0, read);
            }
        } catch (IOException ex) {
            Files.deleteIfExists(temp);
            throw ex;
        }

        if (written == 0L) {
            Files.deleteIfExists(temp);
            throw new IOException("Empty TMDb TV index");
        }

        try {
            Files.move(temp, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        AppLog.info("tmdb-index", "Índice local de series TMDb actualizado: " + filename);
    }

    private static void cleanupOldIndexes(Path dir, Path keep) {
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("tv_series_ids_"))
                    .filter(path -> !path.equals(keep))
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); }
                        catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    private static Path cacheDir() {
        String override = System.getProperty(CACHE_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Local")
                : Path.of(localAppData);
        return base.resolve("Streamflix").resolve("cache").resolve("tmdb-index")
                .toAbsolutePath().normalize();
    }

    private static Integer integer(Object value) {
        if (!(value instanceof Number number)) return null;
        double d = number.doubleValue();
        return Double.isFinite(d) && d > 0 && d <= Integer.MAX_VALUE && d == Math.rint(d)
                ? (int) d : null;
    }
}
