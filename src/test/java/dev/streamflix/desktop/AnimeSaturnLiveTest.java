package dev.streamflix.desktop;

import java.awt.image.BufferedImage;

public final class AnimeSaturnLiveTest {
    public static void main(String[] args) throws Exception {
        Provider p = new AnimeSaturnProvider();
        require(p.name().equals("AnimeSaturn"), "name");
        require(!p.supportsMovies(), "no movies");
        require(p.supportsTvShows(), "supports shows");

        var shows = p.tvShows(1);
        System.out.println("SHOWS=" + shows.size());
        if (shows.isEmpty()) throw new IllegalStateException("AnimeSaturn shows empty");

        var sample = shows.get(0);
        BufferedImage poster = ImageLoader.download(sample.poster());
        System.out.println("FIRST_SHOW=" + sample.title() + " POSTER=" + poster.getWidth() + "x" + poster.getHeight());
        if (poster.getWidth() < 10 || poster.getHeight() < 10) {
            throw new IllegalStateException("Invalid poster dimensions");
        }

        var search = p.search("Solo", 1);
        System.out.println("SEARCH_SOLO=" + search.size());
        if (search.isEmpty()) throw new IllegalStateException("AnimeSaturn search empty");

        var episodes = p.episodes(sample);
        System.out.println("EPISODES=" + episodes.size());
        if (episodes.isEmpty()) throw new IllegalStateException("AnimeSaturn episodes empty");

        var ep = episodes.get(0);
        var servers = p.servers(ep.id());
        System.out.println("SERVERS=" + servers.size());
        if (servers.isEmpty()) throw new IllegalStateException("AnimeSaturn servers empty");

        ExtractorRegistry registry = new ExtractorRegistry();
        Models.Video video = registry.resolve(servers.get(0));
        System.out.println("VIDEO_SOURCE=" + video.source());
        int exit = MpvPlayer.smoke(video);
        System.out.println("MPV_EXIT=" + exit);
        if (exit != 0) throw new IllegalStateException("AnimeSaturn MPV playback failed with " + exit);

        System.out.println("ANIMESATURN_OK");
    }

    private static void require(boolean val, String msg) {
        if (!val) throw new AssertionError("Failed: " + msg);
    }
}
