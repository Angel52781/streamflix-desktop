package dev.streamflix.desktop;

public final class AnimeWorldLiveTest {
    public static void main(String[] args) throws Exception {
        Provider p = new AnimeWorldProvider();
        var movies = p.movies(1);
        var shows = p.tvShows(1);
        System.out.println("MOVIES=" + movies.size() + " SHOWS=" + shows.size());
        if (movies.isEmpty() || shows.isEmpty()) throw new IllegalStateException("AnimeWorld catalog empty");
        var poster = ImageLoader.download(movies.get(0).poster());
        System.out.println("FIRST_MOVIE=" + movies.get(0).title() + " POSTER=" + poster.getWidth() + "x" + poster.getHeight());
        var movieServers = p.servers(movies.get(0).providerId());
        if (movieServers.isEmpty()) throw new IllegalStateException("AnimeWorld movie servers empty");
        int movieCode = MpvPlayer.smoke(new ExtractorRegistry().resolve(movieServers.get(0)));
        System.out.println("MOVIE_MPV=" + movieCode);
        if (movieCode != 0) throw new IllegalStateException("AnimeWorld movie playback failed");
        var episodes = p.episodes(shows.get(0));
        System.out.println("FIRST_SHOW=" + shows.get(0).title() + " EPISODES=" + episodes.size());
        if (episodes.isEmpty()) throw new IllegalStateException("AnimeWorld episodes empty");
        var epServers = p.servers(episodes.get(0).id());
        if (epServers.isEmpty()) throw new IllegalStateException("AnimeWorld episode servers empty");
        int epCode = MpvPlayer.smoke(new ExtractorRegistry().resolve(epServers.get(0)));
        System.out.println("EP_MPV=" + epCode);
        if (epCode != 0) throw new IllegalStateException("AnimeWorld episode playback failed");
        System.out.println("ANIMEWORLD_OK");
    }
}
