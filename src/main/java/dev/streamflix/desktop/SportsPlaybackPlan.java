package dev.streamflix.desktop;

import java.util.List;

record SportsPlaybackPlan(
        SportsEvent event,
        List<SportsChannelHealth> ranked
) {
    List<SportsChannelCandidate> verifiedCandidates() {
        return ranked.stream().filter(SportsChannelHealth::healthy).map(SportsChannelHealth::candidate).toList();
    }

    List<SportsChannelCandidate> allCandidates() {
        return ranked.stream().map(SportsChannelHealth::candidate).toList();
    }
}
