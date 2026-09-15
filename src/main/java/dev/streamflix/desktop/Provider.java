package dev.streamflix.desktop;

import java.util.List;

interface Provider {
    String name();
    List<Models.ShowItem> movies(int page) throws Exception;
    List<Models.ShowItem> tvShows(int page) throws Exception;
    List<Models.ShowItem> search(String query, int page) throws Exception;
    List<Models.Episode> episodes(Models.ShowItem show) throws Exception;
    List<Models.Server> servers(int providerItemId) throws Exception;
}
