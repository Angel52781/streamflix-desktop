package dev.streamflix.desktop;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local per-host playback telemetry.
 *
 * Automatic playback uses observed startup time and failures on this machine
 * instead of guessing by geography. No IP address or location is stored.
 */
final class PlaybackServerStats {
    private static final String DATA_DIR_PROPERTY = "streamflix.data.dir";
    private static final Object LOCK = new Object();

    private PlaybackServerStats() {}

    static List<Models.Server> rank(List<Models.Server> servers) {
        if (servers == null || servers.size() <= 1) return servers == null ? List.of() : List.copyOf(servers);

        Map<String, Stats> stats = readStats();
        ArrayList<Models.Server> ranked = new ArrayList<>(servers);
        ranked.sort(Comparator
                .comparingDouble((Models.Server server) -> score(stats.get(key(server))))
                .thenComparing(Models.Server::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(ranked);
    }

    static void recordSuccess(Models.Server server, long startupMillis) {
        update(server, Math.max(1L, startupMillis), true);
    }

    static void recordFailure(Models.Server server, long startupMillis) {
        update(server, Math.max(1L, startupMillis), false);
    }

    static String key(Models.Server server) {
        if (server == null) return "unknown";
        String src = server.src();
        if (src != null && !src.isBlank()) {
            try {
                URI uri = URI.create(src);
                String host = uri.getHost();
                if (host != null && !host.isBlank()) return host.toLowerCase();
            } catch (RuntimeException ignored) {}
        }
        String name = server.name();
        return name == null || name.isBlank() ? "unknown" : name.strip().toLowerCase();
    }

    private static double score(Stats stats) {
        if (stats == null) return 3500.0;

        // Network/CDN conditions change. Old observations must not permanently
        // bias playback when the laptop moves to another connection or region.
        long age = System.currentTimeMillis() - stats.updatedAt;
        if (stats.updatedAt <= 0 || age > java.util.concurrent.TimeUnit.DAYS.toMillis(7)) {
            return 3500.0;
        }

        int attempts = stats.successes + stats.failures;
        if (stats.successes == 0) return 6500.0 + stats.failures * 900.0;
        double failureRate = attempts == 0 ? 0.0 : (double) stats.failures / attempts;
        return stats.averageStartupMillis + failureRate * 6000.0 - Math.min(5, stats.successes) * 120.0;
    }

    private static void update(Models.Server server, long startupMillis, boolean success) {
        synchronized (LOCK) {
            Map<String, Stats> stats = readStatsUnlocked();
            String key = key(server);
            Stats previous = stats.getOrDefault(key, new Stats(0, 0, 0.0, 0L));

            int successes = previous.successes + (success ? 1 : 0);
            int failures = previous.failures + (success ? 0 : 1);
            double average = previous.averageStartupMillis;

            if (success) {
                average = previous.successes == 0
                        ? startupMillis
                        : (previous.averageStartupMillis * previous.successes + startupMillis) / successes;
            }

            stats.put(key, new Stats(successes, failures, average, System.currentTimeMillis()));
            writeStats(stats);
        }
    }

    private static Map<String, Stats> readStats() {
        synchronized (LOCK) {
            return readStatsUnlocked();
        }
    }

    private static Map<String, Stats> readStatsUnlocked() {
        Path file = statsFile();
        LinkedHashMap<String, Stats> out = new LinkedHashMap<>();
        try {
            if (!Files.isRegularFile(file)) return out;
            Object parsed = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?>)) return out;

            for (var entry : Json.object(parsed).entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?>)) continue;
                Map<String, Object> value = Json.object(entry.getValue());
                Integer successes = Json.integer(value.get("successes"));
                Integer failures = Json.integer(value.get("failures"));
                Double average = Json.decimal(value.get("averageStartupMillis"));
                Integer updatedInt = Json.integer(value.get("updatedAt"));
                long updated = updatedInt == null ? 0L : updatedInt.longValue();

                // JSON integer helper is intentionally 32-bit; preserve compatibility
                // if the timestamp cannot be represented through it.
                Object rawUpdated = value.get("updatedAt");
                if (rawUpdated instanceof Number n) updated = n.longValue();

                out.put(entry.getKey(), new Stats(
                        Math.max(0, successes == null ? 0 : successes),
                        Math.max(0, failures == null ? 0 : failures),
                        Math.max(0.0, average == null ? 0.0 : average),
                        Math.max(0L, updated)
                ));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static void writeStats(Map<String, Stats> stats) {
        Path target = statsFile();
        Path dir = target.getParent();
        Path temp = null;
        try {
            Files.createDirectories(dir);
            LinkedHashMap<String, Object> root = new LinkedHashMap<>();
            for (var entry : stats.entrySet()) {
                Stats value = entry.getValue();
                LinkedHashMap<String, Object> map = new LinkedHashMap<>();
                map.put("successes", value.successes);
                map.put("failures", value.failures);
                map.put("averageStartupMillis", value.averageStartupMillis);
                map.put("updatedAt", value.updatedAt);
                root.put(entry.getKey(), map);
            }

            temp = Files.createTempFile(dir, "server-stats-", ".tmp");
            Files.writeString(temp, Json.stringify(root), StandardCharsets.UTF_8);
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

    private static Path statsFile() {
        String override = System.getProperty(DATA_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize().resolve("server-stats.json");
        }

        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Roaming")
                : Path.of(appData);
        return base.resolve("Streamflix").resolve("server-stats.json").toAbsolutePath().normalize();
    }

    private record Stats(int successes, int failures, double averageStartupMillis, long updatedAt) {}
}
