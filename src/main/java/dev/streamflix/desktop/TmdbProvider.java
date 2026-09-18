package dev.streamflix.desktop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** TMDb metadata provider with desktop playback routes. */
final class TmdbProvider implements Provider {
    private final TmdbClient client;

    TmdbProvider(String language) { this(new TmdbClient(language)); }
    TmdbProvider(TmdbClient client) { this.client = client; }

    @Override public String id() { return "tmdb-" + client.language().substring(0, 2); }
    @Override public String name() { return "TMDb (" + (id().endsWith("es") ? "ES" : "EN") + ")"; }

    @Override public List<Models.ShowItem> movies(int page) throws Exception {
        return listing("discover/movie", page, "movie");
    }

    @Override public List<Models.ShowItem> tvShows(int page) throws Exception {
        return listing("discover/tv", page, "tv");
    }

    List<Models.ShowItem> moviesByGenre(int genreId, int page) throws Exception {
        return listingByGenre("discover/movie", "movie", genreId, page);
    }

    List<Models.ShowItem> tvShowsByGenre(int genreId, int page) throws Exception {
        return listingByGenre("discover/tv", "tv", genreId, page);
    }

    private List<Models.ShowItem> listingByGenre(
            String path, String mediaType, int genreId, int page) throws Exception {
        checkPage(page);
        if (genreId <= 0) throw new IllegalArgumentException("TMDb: Invalid genre.");
        return mapListing(client.get(path, Map.of(
                "page", Integer.toString(page),
                "include_adult", "false",
                "sort_by", "popularity.desc",
                "with_genres", Integer.toString(genreId)
        )), mediaType);
    }

    private List<Models.ShowItem> listing(String path, int page, String mediaType) throws Exception {
        checkPage(page);
        return mapListing(client.get(path, Map.of("page", Integer.toString(page),
                "include_adult", "false", "sort_by", "popularity.desc")), mediaType);
    }

    @Override public List<Models.ShowItem> search(String query, int page) throws Exception {
        checkPage(page);
        if (query == null || query.isBlank()) return List.of();

        String cleaned = query.strip();
        Map<String, String> parameters = Map.of(
                "page", Integer.toString(page),
                "query", cleaned,
                "include_adult", "false");

        LinkedHashMap<String, Models.ShowItem> merged = new LinkedHashMap<>();
        for (Models.ShowItem item : mapListing(
                client.get("search/multi", parameters), null)) {
            merged.putIfAbsent(item.id(), item);
        }

        // TMDb says text search considers original/translated/alternative titles, but
        // localization/indexing can still differ in practice. Merge the other UI
        // language as a resilience fallback while keeping preferred-language items first.
        String fallbackLanguage = client.language().startsWith("es") ? "en-US" : "es-ES";
        TmdbClient fallback = client.withLanguage(fallbackLanguage);
        for (Models.ShowItem item : mapListing(
                fallback.get("search/multi", parameters), null)) {
            merged.putIfAbsent(item.id(), item);
        }
        return List.copyOf(merged.values());
    }

    private List<Models.ShowItem> mapListing(Map<String, Object> root, String fixedType) throws TmdbException {
        var items = new LinkedHashMap<String, Models.ShowItem>();
        for (Object raw : TmdbClient.array(root, "results")) {
            Map<String, Object> value = Json.object(raw);
            String type = fixedType == null ? Json.string(value.get("media_type")) : fixedType;
            if (!type.equals("movie") && !type.equals("tv")) continue;
            Integer remoteId = nonNegativeInteger(value.get("id"));
            if (remoteId == null || remoteId == 0) continue;
            boolean movie = type.equals("movie");
            String title = text(value, movie ? "title" : "name");
            if (title.isBlank()) title = text(value, movie ? "original_title" : "original_name");
            if (title.isBlank()) continue;
            String itemId = "tmdb:" + type + ":" + remoteId;
            items.putIfAbsent(itemId, new Models.ShowItem(itemId, type + "/" + remoteId,
                    title, text(value, "overview"), text(value, movie ? "release_date" : "first_air_date"),
                    nonNegativeInteger(value.get("runtime")), Json.decimal(value.get("vote_average")),
                    image(value.get("poster_path"), "w780"), image(value.get("backdrop_path"), "w1280"),
                    movie ? Models.ShowType.MOVIE : Models.ShowType.TV_SHOW, id()));
        }
        return List.copyOf(items.values());
    }

    @Override public List<Models.Episode> episodes(Models.ShowItem show) throws Exception {
        if (show.type() != Models.ShowType.TV_SHOW) return List.of();
        String seriesPath = show.providerId();
        if (seriesPath == null || !seriesPath.matches("tv/[1-9][0-9]*")) {
            throw new TmdbException("Invalid series identifier.");
        }
        var seasons = new TreeSet<Integer>();
        for (Object raw : TmdbClient.array(client.get(seriesPath, Map.of()), "seasons")) {
            Integer number = nonNegativeInteger(Json.object(raw).get("season_number"));
            if (number != null) seasons.add(number); // Includes specials (season zero).
        }
        var episodes = new LinkedHashMap<String, Models.Episode>();
        for (int season : seasons) {
            String seasonPath = seriesPath + "/season/" + season;
            for (Object raw : TmdbClient.array(client.get(seasonPath, Map.of()), "episodes")) {
                Map<String, Object> value = Json.object(raw);
                Integer number = nonNegativeInteger(value.get("episode_number"));
                if (number == null || number == 0) continue;
                // Remote coordinates remain stable across translations and catalog ordering.
                String episodeId = seasonPath + "/episode/" + number;
                String title = text(value, "name");
                if (title.isBlank()) title = (id().endsWith("es") ? "Episodio " : "Episode ") + number;
                episodes.putIfAbsent(episodeId, new Models.Episode(episodeId, season, number, title,
                        text(value, "overview"), image(value.get("still_path"), "w500")));
            }
        }
        var sorted = new ArrayList<>(episodes.values());
        sorted.sort(Comparator.comparingInt(Models.Episode::seasonNumber).thenComparingInt(Models.Episode::episodeNumber));
        return List.copyOf(sorted);
    }

    @Override public List<Models.Server> servers(String providerItemId) throws TmdbException {
        if (providerItemId == null || providerItemId.isBlank()) return List.of();
        String lang = id().endsWith("es") ? "es" : "en";
        if (providerItemId.matches("movie/[1-9][0-9]*")) {
            String movieId = providerItemId.substring("movie/".length());
            return List.of(new Models.Server(
                    "vixsrc-" + providerItemId,
                    "VixSrc",
                    VixSrcExtractor.MAIN_URL + "/api/movie/" + movieId + "?lang=" + lang));
        }
        var episode = java.util.regex.Pattern.compile(
                "tv/([1-9][0-9]*)/season/([0-9]+)/episode/([1-9][0-9]*)").matcher(providerItemId);
        if (episode.matches()) {
            return List.of(new Models.Server(
                    "vixsrc-" + providerItemId,
                    "VixSrc",
                    VixSrcExtractor.MAIN_URL + "/api/tv/" + episode.group(1) + "/" + episode.group(2)
                            + "/" + episode.group(3) + "?lang=" + lang));
        }
        throw new TmdbException("Invalid playback identifier.");
    }

    private static void checkPage(int page) {
        if (page < 1 || page > 500) throw new IllegalArgumentException("TMDb: Page must be between 1 and 500.");
    }

    private static String text(Map<String, Object> value, String field) {
        return value.get(field) instanceof String s ? s.strip() : "";
    }

    private static Integer nonNegativeInteger(Object value) {
        if (!(value instanceof Number number)) return null;
        double d = number.doubleValue();
        return Double.isFinite(d) && d >= 0 && d <= Integer.MAX_VALUE && d == Math.rint(d) ? (int) d : null;
    }

    private static String image(Object value, String size) {
        if (!(value instanceof String path) || !path.matches("/?[A-Za-z0-9_-]+\\.[A-Za-z0-9]+")) return null;
        return "https://image.tmdb.org/t/p/" + size + (path.startsWith("/") ? path : "/" + path);
    }
}
