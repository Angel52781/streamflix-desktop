package dev.streamflix.desktop;

import java.util.List;

public final class ProviderFixtureTest {
    public static void main(String[] args) {
        String fixture = """
            {"error":false,"data":{"posts":[
              {"_id":101,"title":"Demo Movie","overview":"Overview","slug":"demo-movie","images":{"poster":"/2026/demo.jpg","backdrop":"/2026/back.jpg"},"rating":"7.8","type":"movies","release_date":"2026-01-02","runtime":"95"},
              {"_id":102,"title":"Demo Series","slug":"demo-series","images":{},"rating":"8.1","type":"tvshows","runtime":""}
            ]}}
            """;
        List<Models.ShowItem> items = FanpelisProvider.parseListing(fixture);
        require(items.size() == 2, "count");
        require(items.get(0).type() == Models.ShowType.MOVIE, "movie type");
        require(items.get(1).type() == Models.ShowType.TV_SHOW, "series type");
        require(items.get(0).providerId().equals("101"), "provider id");
        require(items.get(0).poster().contains("wp-content/uploads/2026/demo.jpg"), "poster path");
        require(ProviderRegistry.get("tmdb-en") != null, "TMDb EN registered");
        require(ProviderRegistry.get("tmdb-es") != null, "TMDb ES registered");
        System.out.println("ProviderFixtureTest OK");
    }

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError("Failed: " + name);
    }
}
