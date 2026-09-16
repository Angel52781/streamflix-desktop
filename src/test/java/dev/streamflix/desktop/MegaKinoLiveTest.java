package dev.streamflix.desktop;

import java.awt.image.BufferedImage;

public final class MegaKinoLiveTest {
    public static void main(String[] args) throws Exception {
        Provider p = new MegaKinoProvider();
        require(p.name().equals("MEGAKino"), "name");
        require(p.supportsMovies(), "supports movies");
        require(p.supportsTvShows(), "supports shows");

        var movies = p.movies(1);
        System.out.println("MOVIES=" + movies.size());
        if (movies.isEmpty()) throw new IllegalStateException("MEGAKino movies empty");

        var sample = movies.get(0);
        BufferedImage poster = ImageLoader.download(sample.poster());
        System.out.println("FIRST_MOVIE=" + sample.title() + " POSTER=" + poster.getWidth() + "x" + poster.getHeight());
        if (poster.getWidth() < 10 || poster.getHeight() < 10) {
            throw new IllegalStateException("Invalid poster dimensions");
        }

        var search = p.search("Trockenzeit", 1);
        System.out.println("SEARCH_TROCKENZEIT=" + search.size());
        if (search.isEmpty()) throw new IllegalStateException("MEGAKino search empty");

        var movieServers = p.servers(sample.providerId());
        System.out.println("MOVIE_SERVERS=" + movieServers.size());
        if (movieServers.isEmpty()) throw new IllegalStateException("MEGAKino movie servers empty");

        ExtractorRegistry registry = new ExtractorRegistry();
        Models.Video video = null;
        for (var server : movieServers) {
            try {
                video = registry.resolve(server);
                if (video != null && video.source() != null && !video.source().isBlank()) {
                    System.out.println("RESOLVED=" + server.name() + " URL=" + video.source());
                    break;
                }
            } catch (Exception e) {
                System.out.println("SKIP=" + server.name() + " (" + e.getMessage() + ")");
            }
        }
        if (video == null) throw new IllegalStateException("No movie server could be resolved");

        int exit = MpvPlayer.smoke(video);
        System.out.println("MPV_EXIT=" + exit);
        if (exit != 0) throw new IllegalStateException("MEGAKino MPV playback failed with " + exit);

        var shows = p.tvShows(1);
        System.out.println("SHOWS=" + shows.size());
        if (!shows.isEmpty()) {
            var sampleShow = shows.get(0);
            var episodes = p.episodes(sampleShow);
            System.out.println("FIRST_SHOW=" + sampleShow.title() + " EPISODES=" + episodes.size());
        }

        System.out.println("MEGAKINO_OK");
    }

    private static void require(boolean val, String msg) {
        if (!val) throw new AssertionError("Failed: " + msg);
    }
}
