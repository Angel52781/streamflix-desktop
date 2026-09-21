package dev.streamflix.desktop;

import java.time.*;
import java.util.*;

/** Free-only TheSportsDB v1 adapter. No paid key path is required or supported. */
final class TheSportsDbProvider implements SportsDataProvider {
    private static final String API_KEY = "123";
    private static final String V1 = "https://www.thesportsdb.com/api/v1/json/" + API_KEY + "/";

    // Keep one refresh below TheSportsDB's documented free 30 req/min ceiling.
    private static final List<String> TODAY_SPORTS = List.of(
            "Soccer", "Basketball", "American Football", "Baseball", "Ice Hockey",
            "Tennis", "Motorsport", "Fighting", "Volleyball");
    private static final List<String> UPCOMING_SPORTS = List.of(
            "Soccer", "Basketball", "American Football", "Tennis", "Motorsport");
    private static final List<String> TV_SPORTS = List.of(
            "Soccer", "Basketball", "American Football", "Tennis", "Motorsport", "Fighting");

    private final Http http;
    private volatile long disabledUntil;

    TheSportsDbProvider() { this(new Http()); }
    TheSportsDbProvider(Http http) { this.http = http; }

    @Override public SportsSnapshot load() throws Exception {
        if (System.currentTimeMillis() < disabledUntil) {
            throw new IllegalStateException("TheSportsDB temporalmente en cooldown");
        }
        LocalDate today = LocalDate.now();
        Map<String, List<SportsBroadcaster>> broadcasts = broadcastMap(today);
        List<SportsEvent> todayEvents = scheduleMany(today, TODAY_SPORTS, broadcasts);

        ArrayList<SportsEvent> upcoming = new ArrayList<>();
        try { upcoming.addAll(scheduleMany(today.plusDays(1), UPCOMING_SPORTS, Map.of())); }
        catch (Exception ex) { AppLog.warn("sports", "TheSportsDB próximos +1 degradados", ex); }
        try { upcoming.addAll(scheduleMany(today.plusDays(2), UPCOMING_SPORTS, Map.of())); }
        catch (Exception ex) { AppLog.warn("sports", "TheSportsDB próximos +2 degradados", ex); }

        LinkedHashMap<String, SportsEvent> live = new LinkedHashMap<>();
        ArrayList<SportsEvent> nonLive = new ArrayList<>();
        for (SportsEvent event : todayEvents) {
            if (looksLive(event.status(), event.progress())) live.put(event.id(), event);
            else nonLive.add(event);
        }
        Comparator<SportsEvent> byStart = Comparator.comparing(
                SportsEvent::startsAt, Comparator.nullsLast(Comparator.naturalOrder()));
        nonLive.sort(byStart);
        upcoming.sort(byStart);

        return new SportsSnapshot(List.copyOf(live.values()), List.copyOf(nonLive),
                List.copyOf(upcoming), true, "TheSportsDB");
    }

    private List<SportsEvent> scheduleMany(LocalDate date, List<String> sports,
            Map<String, List<SportsBroadcaster>> broadcasters) throws Exception {
        LinkedHashMap<String, SportsEvent> out = new LinkedHashMap<>();
        Exception last = null;
        for (String sport : sports) {
            try {
                for (SportsEvent event : schedule(date, sport, broadcasters)) out.putIfAbsent(event.id(), event);
            } catch (Exception ex) {
                last = ex;
                if (isRateLimited(ex)) {
                    disabledUntil = System.currentTimeMillis() + 2L * 60L * 1000L;
                    break;
                }
                AppLog.warn("sports", "TheSportsDB falló para " + sport + " en " + date, ex);
            }
        }
        if (out.isEmpty() && last != null) throw last;
        return List.copyOf(out.values());
    }

    private List<SportsEvent> schedule(LocalDate date, String sport,
            Map<String, List<SportsBroadcaster>> broadcasters) throws Exception {
        String url = V1 + "eventsday.php?d=" + date + "&s=" + Http.encode(sport);
        Map<String, Object> root = Json.object(Json.parse(http.get(url,
                Map.of("Accept", "application/json,text/plain,*/*"))));
        return parseEvents(Json.array(root.get("events")), broadcasters);
    }

    private Map<String, List<SportsBroadcaster>> broadcastMap(LocalDate date) {
        LinkedHashMap<String, LinkedHashMap<String, SportsBroadcaster>> grouped = new LinkedHashMap<>();
        for (String sport : TV_SPORTS) {
            try {
                String url = V1 + "eventstv.php?d=" + date + "&s=" + Http.encode(sport);
                Map<String, Object> root = Json.object(Json.parse(http.get(url,
                        Map.of("Accept", "application/json,text/plain,*/*"))));
                for (Object raw : firstArray(root, "tvevents", "eventtv", "tv", "events")) {
                    Map<String, Object> item = Json.object(raw);
                    String eventId = text(item, "idEvent", "id_event", "event_id");
                    String channel = text(item, "strChannel", "strTVStation", "channel");
                    if (eventId.isBlank() || channel.isBlank()) continue;
                    String country = text(item, "strCountry", "country");
                    SportsBroadcaster broadcaster = new SportsBroadcaster(
                            channel, country, blank(text(item, "strLogo", "logo")));
                    String key = channel.toLowerCase(Locale.ROOT) + "|" + country.toLowerCase(Locale.ROOT);
                    grouped.computeIfAbsent(eventId, ignored -> new LinkedHashMap<>())
                            .putIfAbsent(key, broadcaster);
                }
            } catch (Exception ex) {
                if (isRateLimited(ex)) {
                    disabledUntil = System.currentTimeMillis() + 2L * 60L * 1000L;
                    break;
                }
                AppLog.warn("sports", "TheSportsDB TV falló para " + sport, ex);
            }
        }
        LinkedHashMap<String, List<SportsBroadcaster>> out = new LinkedHashMap<>();
        grouped.forEach((id, values) -> out.put(id, List.copyOf(values.values())));
        return out;
    }

    static List<SportsEvent> parseEvents(List<Object> values,
            Map<String, List<SportsBroadcaster>> broadcasters) {
        ArrayList<SportsEvent> out = new ArrayList<>();
        for (Object raw : values) {
            Map<String, Object> item = Json.object(raw);
            String id = text(item, "idEvent", "id");
            if (id.isBlank()) continue;
            String home = blank(text(item, "strHomeTeam", "home_team"));
            String away = blank(text(item, "strAwayTeam", "away_team"));
            String name = blank(text(item, "strEvent", "name"));
            if (name == null) name = home != null && away != null ? home + " vs " + away : "Evento deportivo";
            out.add(new SportsEvent(id, displaySport(text(item, "strSport", "sport")),
                    fallback(text(item, "strLeague", "league"), "Competición"), name,
                    home, away,
                    blank(text(item, "strHomeTeamBadge", "home_badge")),
                    blank(text(item, "strAwayTeamBadge", "away_badge")),
                    integer(item, "intHomeScore", "home_score"), integer(item, "intAwayScore", "away_score"),
                    blank(text(item, "strStatus", "status")), blank(text(item, "strProgress", "progress")),
                    parseStart(item), broadcasters.getOrDefault(id, List.of())));
        }
        return List.copyOf(out);
    }

    private static Instant parseStart(Map<String, Object> item) {
        String timestamp = text(item, "strTimestamp", "timestamp");
        if (!timestamp.isBlank()) {
            try { return Instant.parse(timestamp); } catch (Exception ignored) {}
            try { return OffsetDateTime.parse(timestamp).toInstant(); } catch (Exception ignored) {}
            try { return LocalDateTime.parse(timestamp.replace(" ", "T")).toInstant(ZoneOffset.UTC); }
            catch (Exception ignored) {}
        }
        String date = text(item, "dateEventLocal", "dateEvent", "date");
        String time = text(item, "strTimeLocal", "strTime", "time");
        if (date.isBlank()) return null;
        try {
            LocalTime clock = time.isBlank() ? LocalTime.NOON : LocalTime.parse(normalizeTime(time));
            return LocalDate.parse(date).atTime(clock).atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception ignored) { return null; }
    }

    private static String normalizeTime(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 8) return trimmed.substring(0, 8);
        if (trimmed.length() == 5) return trimmed + ":00";
        return trimmed;
    }

    static boolean looksLive(String status, String progress) {
        String combined = ((status == null ? "" : status) + " " + (progress == null ? "" : progress))
                .toLowerCase(Locale.ROOT);
        if (combined.isBlank()) return false;
        if (combined.contains("not started") || combined.contains("scheduled")
                || combined.contains("final") || combined.contains("finished")
                || combined.contains("postponed") || combined.contains("cancel")
                || combined.matches(".*\\b(ns|ft|aet|aot|pen|pst|canc)\\b.*")) return false;
        return combined.contains("live") || combined.contains("quarter") || combined.contains("period")
                || combined.contains("inning") || combined.contains("set ") || combined.contains("lap ")
                || combined.contains("half") || combined.contains("in progress")
                || combined.matches(".*\\b(1h|2h|ht|q[1-4]|ot|ip)\\b.*")
                || combined.matches(".*\\b[0-9]{1,3}['′].*");
    }

    static String displaySport(String raw) {
        if (raw == null || raw.isBlank()) return "Otros";
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "soccer", "football" -> "Fútbol";
            case "basketball" -> "Básquet";
            case "tennis" -> "Tenis";
            case "motorsport", "motor sport", "formula 1" -> "Automovilismo";
            case "fighting", "boxing", "mma" -> "Combate";
            case "american football" -> "Fútbol americano";
            case "ice hockey", "hockey" -> "Hockey";
            case "baseball" -> "Béisbol";
            case "volleyball" -> "Vóley";
            case "golf" -> "Golf";
            case "rugby" -> "Rugby";
            case "cricket" -> "Cricket";
            case "athletics" -> "Atletismo";
            case "cycling" -> "Ciclismo";
            default -> raw.trim();
        };
    }

    private static List<Object> firstArray(Map<String, Object> root, String... keys) {
        for (String key : keys) if (root.get(key) instanceof List<?>) return Json.array(root.get(key));
        return List.of();
    }
    private static String text(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            String value = Json.string(item.get(key));
            if (!value.isBlank() && !"null".equalsIgnoreCase(value)) return value.trim();
        }
        return "";
    }
    private static Integer integer(Map<String, Object> item, String... keys) {
        for (String key : keys) { Integer value = Json.integer(item.get(key)); if (value != null) return value; }
        return null;
    }
    private static String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value; }
    private static boolean isRateLimited(Exception ex) {
        String message = ex.getMessage();
        return message != null && message.contains("HTTP 429");
    }
}
