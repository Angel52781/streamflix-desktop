package dev.streamflix.desktop;

import java.time.Instant;
import java.util.List;

public final class SportsDataTest {
    public static void main(String[] args) {
        testSportScoreFixture();
        testCompositeDeduplication();
        System.out.println("SportsDataTest OK");
    }

    private static void testSportScoreFixture() {
        Object parsed = Json.parse("""
                [{
                  "slug":"barcelona-vs-real-madrid",
                  "home":"Barcelona",
                  "away":"Real Madrid",
                  "home_score":2,
                  "away_score":1,
                  "status":"live",
                  "competition":"LaLiga",
                  "time":"2026-09-21T20:00:00+00:00"
                }]
                """);
        List<SportsEvent> events = SportScoreProvider.parseMatches(Json.array(parsed), "football");
        require(events.size() == 1, "fixture should produce one event");
        SportsEvent event = events.get(0);
        require("Fútbol".equals(event.sport()), "football should localize to Fútbol");
        require("Barcelona".equals(event.homeTeam()), "home team should parse");
        require(event.homeScore() == 2 && event.awayScore() == 1, "scores should parse");
        require(event.startsAt() != null, "kickoff should parse");
    }

    private static void testCompositeDeduplication() {
        Instant start = Instant.parse("2026-09-21T20:00:00Z");
        SportsEvent sparse = new SportsEvent("a", "Fútbol", "LaLiga", "Barcelona vs Real Madrid",
                "Barcelona", "Real Madrid", null, null, null, null, "NS", null, start, List.of());
        SportsEvent rich = new SportsEvent("b", "Fútbol", "LaLiga", "Barcelona vs Real Madrid",
                "Barcelona", "Real Madrid", "home.png", "away.png", 2, 1, "Live", "67'", start,
                List.of(new SportsBroadcaster("ESPN", "Peru", null)));
        SportsSnapshot merged = CompositeSportsProvider.merge(List.of(
                new SportsSnapshot(List.of(), List.of(sparse), List.of(), true, "A"),
                new SportsSnapshot(List.of(rich), List.of(), List.of(), false, "B")));
        require(merged.live().size() == 1, "same cross-source event should deduplicate");
        require(merged.today().isEmpty(), "live event must not also appear in Today");
        require(merged.live().get(0).broadcasters().size() == 1, "richer broadcaster data should survive");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
