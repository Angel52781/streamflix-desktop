package dev.streamflix.desktop;

public final class RidoMoviesLiveTest {
    public static void main(String[] args) throws Exception {
        Provider p = new RidoMoviesProvider();
        var movies = p.movies(1);
        if (movies.isEmpty()) throw new IllegalStateException("Rido movies empty");
        var poster = ImageLoader.download(movies.get(0).poster());
        System.out.println("MOVIES=" + movies.size() + " POSTER=" + poster.getWidth() + "x" + poster.getHeight());
        var movieServers = p.servers(movies.get(0).providerId());
        if (movieServers.isEmpty()) throw new IllegalStateException("Rido movie servers empty");
        var registry = new ExtractorRegistry();
        Models.Video movieVideo = registry.resolve(movieServers.get(0));
        int movieSmoke = MpvPlayer.smoke(movieVideo);
        System.out.println("MOVIE=" + movies.get(0).title() + " SERVER=" + movieServers.get(0).src() + " MPV=" + movieSmoke);
        if (movieSmoke != 0) throw new IllegalStateException("Rido movie playback failed: " + movieSmoke);
        var shows = p.tvShows(1);
        if (shows.isEmpty()) throw new IllegalStateException("Rido shows empty");
        var episodes = p.episodes(shows.get(0));
        if (episodes.isEmpty()) throw new IllegalStateException("Rido episodes empty");
        var epServers = p.servers(episodes.get(0).id());
        if (epServers.isEmpty()) throw new IllegalStateException("Rido episode servers empty");
        Models.Video episodeVideo = registry.resolve(epServers.get(0));
        int epSmoke = MpvPlayer.smoke(episodeVideo);
        System.out.println("SHOW=" + shows.get(0).title() + " EPISODES=" + episodes.size() + " MPV=" + epSmoke);
        if (epSmoke != 0) throw new IllegalStateException("Rido episode playback failed: " + epSmoke);
        System.out.println("RIDO_LIVE_OK");
    }
}
