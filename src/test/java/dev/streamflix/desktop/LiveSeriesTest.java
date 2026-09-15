package dev.streamflix.desktop;

public final class LiveSeriesTest {
    public static void main(String[] args) throws Exception {
        FanpelisProvider provider = new FanpelisProvider();
        var shows = provider.tvShows(1);
        if (shows.isEmpty()) throw new IllegalStateException("No TV shows");
        var show = shows.get(0);
        System.out.println("SHOW=" + show.title());
        var episodes = provider.episodes(show);
        if (episodes.isEmpty()) throw new IllegalStateException("No episodes for " + show.title());
        var episode = episodes.get(0);
        System.out.println("EP=T" + episode.seasonNumber() + "E" + episode.episodeNumber());
        var servers = provider.servers(episode.id());
        if (servers.isEmpty()) throw new IllegalStateException("No episode servers");
        ExtractorRegistry registry = new ExtractorRegistry();
        for (var server : servers) {
            try {
                var video = registry.resolve(server);
                if (video.source() != null && !video.source().isBlank()) {
                    System.out.println("RESOLVED=" + server.name() + " URL=" + video.source());
                    return;
                }
            } catch (Exception e) { System.out.println("SKIP=" + server.name() + " :: " + e.getMessage()); }
        }
        throw new IllegalStateException("No episode server resolved");
    }
}
