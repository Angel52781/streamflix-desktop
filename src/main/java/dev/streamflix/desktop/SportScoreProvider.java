package dev.streamflix.desktop;

import java.time.*;
import java.util.*;

/**
 * Free/no-key SportScore adapter. Their free tier requires visible attribution,
 * so callers must keep the provider name/source visible in the Sports UI.
 */
final class SportScoreProvider implements SportsDataProvider {
    private static final String BASE = "https://sportscore.com/api/widget/matches/";
    private static final List<String> SPORTS = List.of("football", "basketball", "cricket", "tennis");
    private final Http http;
    private volatile long disabledUntil;

    SportScoreProvider() { this(new Http()); }
    SportScoreProvider(Http http) { this.http = http; }

    @Override public SportsSnapshot load() throws Exception {
        if (System.currentTimeMillis() < disabledUntil) {
            throw new IllegalStateException("SportScore temporalmente en cooldown");
        }

        ArrayList<SportsEvent> all = new ArrayList<>();
        Exception last = null;
        for (String sport : SPORTS) {
            try {
                String url = BASE + "?sport=" + sport + "&limit=50&src=streamflix-desktop";
                String body = http.get(url, Map.of(
                        "Accept", "application/json",
                        "User-Agent", "StreamflixDesktop/1 (+https://github.com/Angel52781/streamflix-desktop)"));
                Map<String, Object> root = Json.object(Json.parse(body));
                all.addAll(parseMatches(Json.array(root.get("matches")), sport));
            } catch (Exception ex) {
                last = ex;
                AppLog.warn("sports", "SportScore falló para " + sport, ex);
            }
        }
        if (all.isEmpty()) {
            disabledUntil = System.currentTimeMillis() + 10L * 60L * 1000L;
            if (last != null) throw last;
        }

        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        ArrayList<SportsEvent> live = new ArrayList<>();
        ArrayList<SportsEvent> todayEvents = new ArrayList<>();
        ArrayList<SportsEvent> upcoming = new ArrayList<>();
        for (SportsEvent event : all) {
            if (isLive(event.status())) {
                live.add(event);
                continue;
            }
            if (event.startsAt() == null) {
                todayEvents.add(event);
                continue;
            }
            LocalDate day = event.startsAt().atZone(zone).toLocalDate();
            if (day.equals(today)) todayEvents.add(event);
            else if (day.isAfter(today)) upcoming.add(event);
        }
        Comparator<SportsEvent> byStart = Comparator.comparing(
                SportsEvent::startsAt, Comparator.nullsLast(Comparator.naturalOrder()));
        live.sort(byStart); todayEvents.sort(byStart); upcoming.sort(byStart);
        return new SportsSnapshot(live, todayEvents, upcoming, false, "Powered by SportScore");
    }

    static List<SportsEvent> parseMatches(List<Object> values, String sport) {
        ArrayList<SportsEvent> out = new ArrayList<>();
        for (Object raw : values) {
            Map<String, Object> item = Json.object(raw);
            String home = text(item, "home", "home_name", "home_team");
            String away = text(item, "away", "away_name", "away_team");
            String slug = text(item, "slug", "id", "match_id");
            String league = fallback(text(item, "competition", "league", "tournament"), "Competición");
            String timestamp = text(item, "time", "kickoff", "start_time", "date");
            Instant startsAt = parseInstant(timestamp);
            String id = !slug.isBlank() ? "sportscore:" + sport + ":" + slug
                    : "sportscore:" + sport + ":" + Integer.toUnsignedString(
                            Objects.hash(home, away, league, timestamp));
            String title = (!home.isBlank() && !away.isBlank()) ? home + " vs " + away
                    : fallback(text(item, "name", "title"), "Evento deportivo");
            out.add(new SportsEvent(
                    id,
                    TheSportsDbProvider.displaySport(sport),
                    league,
                    title,
                    blank(home), blank(away),
                    blank(text(item, "home_logo", "home_badge", "home_image")),
                    blank(text(item, "away_logo", "away_badge", "away_image")),
                    integer(item, "home_score", "score_home"),
                    integer(item, "away_score", "score_away"),
                    blank(text(item, "status", "state")),
                    blank(text(item, "minute", "progress", "clock")),
                    startsAt,
                    List.of()));
        }
        return List.copyOf(out);
    }

    private static boolean isLive(String status) {
        if (status == null) return false;
        String value = status.toLowerCase(Locale.ROOT);
        return value.contains("live") || value.contains("progress") || value.contains("playing")
                || value.matches(".*\\b(1h|2h|ht|q[1-4]|ot|set)\\b.*");
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); } catch (Exception ignored) {}
        try { return OffsetDateTime.parse(value).toInstant(); } catch (Exception ignored) {}
        try { return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant(); }
        catch (Exception ignored) { return null; }
    }

    private static String text(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = Json.string(map.get(key));
            if (!value.isBlank() && !"null".equalsIgnoreCase(value)) return value.trim();
        }
        return "";
    }
    private static Integer integer(Map<String, Object> map, String... keys) {
        for (String key : keys) { Integer value = Json.integer(map.get(key)); if (value != null) return value; }
        return null;
    }
    private static String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value; }
}
