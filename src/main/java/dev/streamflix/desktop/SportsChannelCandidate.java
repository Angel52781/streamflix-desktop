package dev.streamflix.desktop;

record SportsChannelCandidate(
        SportsBroadcaster broadcaster,
        String providerId,
        String providerName,
        Models.ShowItem channel,
        int score
) {}
