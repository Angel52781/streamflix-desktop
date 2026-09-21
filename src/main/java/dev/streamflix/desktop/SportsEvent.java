package dev.streamflix.desktop;

import java.time.Instant;
import java.util.List;

record SportsEvent(
        String id,
        String sport,
        String league,
        String name,
        String homeTeam,
        String awayTeam,
        String homeBadge,
        String awayBadge,
        Integer homeScore,
        Integer awayScore,
        String status,
        String progress,
        Instant startsAt,
        List<SportsBroadcaster> broadcasters
) {
    SportsEvent {
        broadcasters = broadcasters == null ? List.of() : List.copyOf(broadcasters);
    }

    boolean hasTeams() {
        return homeTeam != null && !homeTeam.isBlank() && awayTeam != null && !awayTeam.isBlank();
    }
}
