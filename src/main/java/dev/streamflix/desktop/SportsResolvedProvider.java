package dev.streamflix.desktop;

import java.util.ArrayList;
import java.util.List;

/** Event-scoped provider that feeds multiple IPTV signals into normal playback fallback. */
final class SportsResolvedProvider implements Provider {
    private final SportsEvent event;
    private final List<SportsChannelCandidate> candidates;

    SportsResolvedProvider(SportsEvent event, List<SportsChannelCandidate> candidates) {
        this.event = event;
        this.candidates = List.copyOf(candidates);
    }

    @Override public String id() { return "sports-live:" + event.id(); }
    @Override public String name() { return "Deportes en vivo"; }
    @Override public boolean supportsMovies() { return true; }
    @Override public boolean supportsTvShows() { return false; }
    @Override public List<Models.ShowItem> movies(int page) { return List.of(); }
    @Override public List<Models.ShowItem> tvShows(int page) { return List.of(); }
    @Override public List<Models.ShowItem> search(String query, int page) { return List.of(); }
    @Override public List<Models.Episode> episodes(Models.ShowItem show) { return List.of(); }

    @Override public List<Models.Server> servers(String providerItemId) {
        ArrayList<Models.Server> out = new ArrayList<>();
        int i = 0;
        for (SportsChannelCandidate candidate : candidates) {
            String country = candidate.broadcaster().country().isBlank()
                    ? "" : " · " + candidate.broadcaster().country();
            out.add(new Models.Server(
                    "sports-" + (++i) + "-" + Integer.toUnsignedString(candidate.channel().providerId().hashCode()),
                    candidate.channel().title() + country,
                    M3uStreamExtractor.SCHEME + candidate.channel().providerId()));
        }
        return List.copyOf(out);
    }

    Models.ShowItem item() {
        String title = event.hasTeams() ? event.homeTeam() + " vs " + event.awayTeam() : event.name();
        String overview = event.sport() + " · " + event.league();
        String artwork = event.homeBadge() != null ? event.homeBadge() : event.awayBadge();
        return new Models.ShowItem(
                "sports:event:" + event.id(), event.id(), title, overview,
                event.startsAt() == null ? null : event.startsAt().toString(),
                null, null, artwork, artwork, Models.ShowType.MOVIE, id());
    }
}
