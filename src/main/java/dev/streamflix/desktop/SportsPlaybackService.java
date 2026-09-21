package dev.streamflix.desktop;

import java.util.List;

final class SportsPlaybackService {
    private final SportsChannelResolver resolver;
    private final SportsStreamHealth health = new SportsStreamHealth();

    SportsPlaybackService(SportsChannelResolver resolver) {
        this.resolver = resolver;
    }

    SportsPlaybackPlan resolve(SportsEvent event) {
        List<SportsChannelCandidate> candidates = resolver.resolve(event);
        return new SportsPlaybackPlan(event, health.rank(candidates));
    }
}
