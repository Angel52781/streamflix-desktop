package dev.streamflix.desktop;

import java.util.List;

interface Provider {
    default String id() { return name().toLowerCase().replace(" ", "-"); }
    String name();
    default boolean supportsMovies() { return true; }
    default boolean supportsTvShows() { return true; }

    List<Models.ShowItem> movies(int page) throws Exception;
    List<Models.ShowItem> tvShows(int page) throws Exception;
    List<Models.ShowItem> search(String query, int page) throws Exception;
    List<Models.Episode> episodes(Models.ShowItem show) throws Exception;
    List<Models.Server> servers(String providerItemId) throws Exception;
}
