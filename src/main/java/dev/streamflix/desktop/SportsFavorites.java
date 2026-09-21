package dev.streamflix.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

final class SportsFavorites {
    private static final String DATA_DIR_PROPERTY = "streamflix.data.dir";
    private static final Path FILE = resolveDataDir().resolve("sports-favorites.json");
    private static final List<SportsFavorite> values = new CopyOnWriteArrayList<>();

    static { load(); }
    private SportsFavorites() {}

    static List<SportsFavorite> all() { return List.copyOf(values); }

    static boolean contains(String kind, String name, String sport) {
        return values.stream().anyMatch(value -> same(value, kind, name, sport));
    }

    static void toggle(String kind, String name, String sport) {
        if (name == null || name.isBlank()) return;
        if (contains(kind, name, sport)) values.removeIf(value -> same(value, kind, name, sport));
        else values.add(0, new SportsFavorite(kind, name, sport));
        save();
    }

    static boolean matches(SportsEvent event) {
        if (event == null) return false;
        return values.stream().anyMatch(value -> switch (value.kind()) {
            case "competition" -> equals(value.name(), event.league()) && sportMatches(value, event);
            default -> (equals(value.name(), event.homeTeam()) || equals(value.name(), event.awayTeam()))
                    && sportMatches(value, event);
        });
    }

    private static boolean sportMatches(SportsFavorite value, SportsEvent event) {
        return value.sport().isBlank() || equals(value.sport(), event.sport());
    }

    private static boolean same(SportsFavorite value, String kind, String name, String sport) {
        return equals(value.kind(), kind) && equals(value.name(), name) && equals(value.sport(), sport);
    }

    private static boolean equals(String a, String b) {
        return Objects.equals(normalize(a), normalize(b));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void load() {
        try {
            if (!Files.exists(FILE)) return;
            for (Object raw : Json.array(Json.parse(Files.readString(FILE, StandardCharsets.UTF_8)))) {
                Map<String, Object> map = Json.object(raw);
                String name = Json.string(map.get("name"));
                if (!name.isBlank()) values.add(new SportsFavorite(
                        Json.string(map.get("kind")), name, Json.string(map.get("sport"))));
            }
        } catch (Exception ex) {
            AppLog.warn("sports", "No se pudieron cargar favoritos deportivos", ex);
        }
    }

    private static void save() {
        try {
            ArrayList<Object> out = new ArrayList<>();
            for (SportsFavorite value : values) {
                LinkedHashMap<String, Object> map = new LinkedHashMap<>();
                map.put("kind", value.kind()); map.put("name", value.name()); map.put("sport", value.sport());
                out.add(map);
            }
            writeAtomically(FILE, Json.stringify(out));
        } catch (Exception ex) {
            AppLog.warn("sports", "No se pudieron guardar favoritos deportivos", ex);
        }
    }

    private static Path resolveDataDir() {
        String override = System.getProperty(DATA_DIR_PROPERTY);
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath().normalize();
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) appData = System.getProperty("user.home") + "/AppData/Roaming";
        return Path.of(appData, "Streamflix").toAbsolutePath().normalize();
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
