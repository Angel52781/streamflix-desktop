package dev.streamflix.desktop;

import java.util.List;

record SportsSnapshot(
        List<SportsEvent> live,
        List<SportsEvent> today,
        List<SportsEvent> upcoming,
        boolean limitedCoverage,
        String sourceName
) {}
