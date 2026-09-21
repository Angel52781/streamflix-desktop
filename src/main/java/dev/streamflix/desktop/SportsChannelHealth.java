package dev.streamflix.desktop;

record SportsChannelHealth(
        SportsChannelCandidate candidate,
        boolean healthy,
        long latencyMillis
) {}
