package dev.streamflix.desktop;

import java.util.List;

public final class MainFrameSearchTest {
    public static void main(String[] args) {
        testScopeNormalization();
        testScopedFiltering();
        System.out.println("MainFrameSearchTest OK");
    }

    private static void testScopeNormalization() {
        require(MainFrame.normalizedSearchScope(MainFrame.Mode.MOVIES) == MainFrame.Mode.MOVIES,
                "movies search scope");
        require(MainFrame.normalizedSearchScope(MainFrame.Mode.SERIES) == MainFrame.Mode.SERIES,
                "series search scope");
        require(MainFrame.normalizedSearchScope(MainFrame.Mode.LIVE) == MainFrame.Mode.LIVE,
                "live search scope");
        require(MainFrame.normalizedSearchScope(MainFrame.Mode.HOME) == MainFrame.Mode.HOME,
                "home global search scope");
        require(MainFrame.normalizedSearchScope(MainFrame.Mode.FAVORITES) == MainFrame.Mode.HOME,
                "favorites falls back to global search");
    }

    private static void testScopedFiltering() {
        Models.ShowItem movie = item("tmdb:movie:1", Models.ShowType.MOVIE);
        Models.ShowItem series = item("tmdb:tv:2", Models.ShowType.TV_SHOW);
        List<Models.ShowItem> mixed = List.of(movie, series);

        List<Models.ShowItem> movies =
                MainFrame.filterSearchResults(mixed, MainFrame.Mode.MOVIES);
        require(movies.size() == 1 && movies.get(0).type() == Models.ShowType.MOVIE,
                "movies search excludes series");

        List<Models.ShowItem> shows =
                MainFrame.filterSearchResults(mixed, MainFrame.Mode.SERIES);
        require(shows.size() == 1 && shows.get(0).type() == Models.ShowType.TV_SHOW,
                "series search excludes movies");

        List<Models.ShowItem> global =
                MainFrame.filterSearchResults(mixed, MainFrame.Mode.HOME);
        require(global.size() == 2, "home search remains global");

        List<Models.ShowItem> live =
                MainFrame.filterSearchResults(mixed, MainFrame.Mode.LIVE);
        require(live.size() == 2, "live provider owns its result type filtering");
    }

    private static Models.ShowItem item(String id, Models.ShowType type) {
        return new Models.ShowItem(
                id, id, id, null, null, null, null, null, null, type, "fixture");
    }

    private static void require(boolean value, String label) {
        if (!value) throw new AssertionError("Failed: " + label);
    }
}
