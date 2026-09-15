package dev.streamflix.desktop;

import java.util.*;

final class FanpelisProvider implements Provider {
    static final String ROOT = "https://fanpelis.to/";
    static final String API = ROOT + "api/rest/";
    private final Http http = new Http();

    @Override public String name() { return "FanPelis"; }

    @Override public List<Models.ShowItem> movies(int page) throws Exception {
        return listing(page, "movies");
    }

    @Override public List<Models.ShowItem> tvShows(int page) throws Exception {
        return listing(page, "tvshows");
    }

    @Override public List<Models.ShowItem> search(String query, int page) throws Exception {
        if (query == null || query.isBlank()) return List.of();
        String url = API + "search?query=" + Http.encode(query) + "&page=" + page + "&post_type=movies%2Ctvshows%2Canimes&posts_per_page=24";
        return parseListing(http.get(url));
    }

    private List<Models.ShowItem> listing(int page, String postType) throws Exception {
        String url = API + "listing?page=" + page + "&post_type=" + postType + "&posts_per_page=24";
        return parseListing(http.get(url));
    }

    static List<Models.ShowItem> parseListing(String json) {
        Map<String, Object> root = Json.object(Json.parse(json));
        Map<String, Object> data = Json.object(root.get("data"));
        List<Object> posts = Json.array(data.get("posts"));
        ArrayList<Models.ShowItem> result = new ArrayList<>();
        for (Object raw : posts) {
            Models.ShowItem item = toShow(Json.object(raw));
            if (item != null) result.add(item);
        }
        return result;
    }

    @Override public List<Models.Episode> episodes(Models.ShowItem show) throws Exception {
        String url = API + "episodes?post_id=" + show.providerId();
        Map<String, Object> root = Json.object(Json.parse(http.get(url)));
        List<Object> data = Json.array(root.get("data"));
        ArrayList<Models.Episode> out = new ArrayList<>();
        for (Object raw : data) {
            Map<String, Object> item = Json.object(raw);
            Integer id = Json.integer(item.get("_id"));
            Integer season = Json.integer(item.get("season_number"));
            Integer ep = Json.integer(item.get("episode_number"));
            if (id == null || season == null || ep == null) continue;
            out.add(new Models.Episode(
                    id, season, ep,
                    Json.string(item.get("title")),
                    optional(item.get("overview")),
                    image(optional(item.get("still_path")))
            ));
        }
        out.sort(Comparator.comparingInt(Models.Episode::seasonNumber).thenComparingInt(Models.Episode::episodeNumber));
        return out;
    }

    @Override public List<Models.Server> servers(int providerItemId) throws Exception {
        String url = API + "player?post_id=" + providerItemId + "&_any=1";
        Map<String, Object> root = Json.object(Json.parse(http.get(url)));
        Map<String, Object> data = Json.object(root.get("data"));
        List<Object> embeds = Json.array(data.get("embeds"));
        LinkedHashMap<String, Models.Server> unique = new LinkedHashMap<>();
        int index = 1;
        for (Object raw : embeds) {
            Map<String, Object> embed = Json.object(raw);
            String src = Json.string(embed.get("url"));
            if (src.isBlank()) continue;
            String host = host(src);
            String label = host.isBlank() ? "Server " + index : host;
            String lang = optional(embed.get("lang"));
            String quality = optional(embed.get("quality"));
            if (lang != null && !lang.isBlank()) label += " · " + lang;
            if (quality != null && !quality.isBlank()) label += " · " + quality;
            unique.putIfAbsent(src, new Models.Server(src, label, src));
            index++;
        }
        return List.copyOf(unique.values());
    }

    private static Models.ShowItem toShow(Map<String, Object> item) {
        Integer providerId = Json.integer(item.get("_id"));
        if (providerId == null) return null;
        String type = Json.string(item.get("type"));
        Models.ShowType showType = switch (type) {
            case "movies" -> Models.ShowType.MOVIE;
            case "tvshows", "animes" -> Models.ShowType.TV_SHOW;
            default -> null;
        };
        if (showType == null) return null;
        String slug = Json.string(item.get("slug"));
        String title = Json.string(item.get("title"));
        Map<String, Object> images = Json.object(item.get("images"));
        Integer runtime = Json.integer(item.get("runtime"));
        return new Models.ShowItem(
                slug.isBlank() ? String.valueOf(providerId) : slug,
                providerId,
                title.isBlank() ? "Sin título" : title,
                optional(item.get("overview")),
                optional(item.get("release_date")),
                runtime,
                Json.decimal(item.get("rating")),
                image(optional(images.get("poster"))),
                image(optional(images.get("backdrop"))),
                showType
        );
    }

    private static String optional(Object value) {
        String s = Json.string(value);
        return s.isBlank() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static String image(String path) {
        if (path == null || path.isBlank()) return null;
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        return ROOT + "wp-content/uploads" + (path.startsWith("/") ? path : "/" + path);
    }

    private static String host(String url) {
        try { return java.net.URI.create(url).getHost(); }
        catch (Exception ignored) { return ""; }
    }
}
