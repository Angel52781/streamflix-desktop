package dev.streamflix.desktop;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Opt-in live TMDb metadata gate. Uses the locally configured TMDb credential
 * without printing or persisting it anywhere else.
 */
public final class TmdbCatalogLiveTest {
    public static void main(String[] args) throws Exception {
        for (String language : List.of("en", "es")) {
            smoke(language);
        }
        System.out.println("TMDB_CATALOG_LIVE_OK en es");
    }

    private static void smoke(String language) throws Exception {
        TmdbProvider provider = new TmdbProvider(language);

        List<Models.ShowItem> movies = provider.movies(1);
        require(!movies.isEmpty(), language + ": movie catalog empty");

        List<Models.ShowItem> shows = provider.tvShows(1);
        require(!shows.isEmpty(), language + ": TV catalog empty");

        List<Models.ShowItem> search = provider.search("The Mentalist", 1);
        require(!search.isEmpty(), language + ": search empty");

        Models.ShowItem posterItem = search.stream()
                .filter(item -> item.poster() != null && !item.poster().isBlank())
                .findFirst()
                .orElseThrow(() -> new AssertionError(language + ": no poster in search results"));

        BufferedImage poster = ImageLoader.download(posterItem.poster());
        require(poster.getWidth() > 0 && poster.getHeight() > 0,
                language + ": poster did not decode");

        Models.ShowItem tv = search.stream()
                .filter(item -> item.type() == Models.ShowType.TV_SHOW)
                .findFirst()
                .orElseThrow(() -> new AssertionError(language + ": no TV result"));

        List<Models.Episode> episodes = provider.episodes(tv);
        require(!episodes.isEmpty(), language + ": no episodes");

        System.out.println("TMDB_" + language.toUpperCase()
                + "_OK movies=" + movies.size()
                + " shows=" + shows.size()
                + " search=" + search.size()
                + " episodes=" + episodes.size()
                + " poster=" + poster.getWidth() + "x" + poster.getHeight());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
