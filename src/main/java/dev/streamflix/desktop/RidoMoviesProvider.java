package dev.streamflix.desktop;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.*;

final class RidoMoviesProvider implements Provider {
    private static final String ROOT = "https://ridomovies.su/";
    private final Http http = new Http();

    @Override public String name() { return "RidoMovies"; }

    @Override public List<Models.ShowItem> movies(int page) throws Exception {
        return parseApi(http.get(ROOT + "api/movies/latest?page=" + page, apiHeaders()), "movies", Models.ShowType.MOVIE);
    }

    @Override public List<Models.ShowItem> tvShows(int page) throws Exception {
        return parseApi(http.get(ROOT + "api/tv/latest?page=" + page, apiHeaders()), "series", Models.ShowType.TV_SHOW);
    }

    @Override public List<Models.ShowItem> search(String query, int page) throws Exception {
        if (query == null || query.isBlank()) return List.of();
        String url = ROOT + "api/search?q=" + Http.encode(query) + "&page=" + page + "&lang=en&limit=24";
        Map<String,Object> root = Json.object(Json.parse(http.get(url, apiHeaders())));
        List<Object> data = Json.array(root.get("data"));
        ArrayList<Models.ShowItem> out = new ArrayList<>();
        for (Object raw : data) {
            Map<String,Object> v = Json.object(raw);
            Models.ShowType type = "movie".equals(Json.string(v.get("type"))) ? Models.ShowType.MOVIE : Models.ShowType.TV_SHOW;
            Models.ShowItem item = toItem(v, type);
            if (item != null) out.add(item);
        }
        return out;
    }
    @Override public List<Models.Episode> episodes(Models.ShowItem show) throws Exception {
        if (show.type() != Models.ShowType.TV_SHOW) return List.of();
        Document detail = doc(ROOT + "tv/" + show.providerId());
        LinkedHashSet<Integer> seasons = new LinkedHashSet<>();
        for (Element button : detail.select(".season-tabs button[data-season-number]")) {
            try { seasons.add(Integer.parseInt(button.attr("data-season-number"))); }
            catch (Exception ignored) {}
        }
        if (seasons.isEmpty()) seasons.add(1);
        ArrayList<Models.Episode> out = new ArrayList<>();
        for (int season : seasons) {
            Document seasonDoc = doc(ROOT + "tv/" + show.providerId() + "/season-" + season + "/episode-1");
            for (Element ep : seasonDoc.select(".episodes-grid .episode-link")) {
                String href = ep.attr("href").replaceFirst("^/", "");
                String code = ep.selectFirst(".ep-title-row") == null ? "" : ep.selectFirst(".ep-title-row").text();
                Integer number = numberAfter(code, "Episode ");
                if (number == null) number = numberAfter(href, "episode-");
                if (number == null) continue;
                String title = ep.selectFirst(".ep-name-row") == null ? null : ep.selectFirst(".ep-name-row").text();
                out.add(new Models.Episode(href, season, number, title, null, null));
            }
        }
        out.sort(Comparator.comparingInt(Models.Episode::seasonNumber).thenComparingInt(Models.Episode::episodeNumber));
        return out;
    }

    @Override public List<Models.Server> servers(String providerItemId) throws Exception {
        String path = providerItemId.startsWith("tv/") ? providerItemId : "movie/" + providerItemId;
        Document document = doc(ROOT + path);
        LinkedHashMap<String,Models.Server> out = new LinkedHashMap<>();
        Element cover = document.selectFirst("#player-cover[data-embed]");
        addEmbed(out, cover == null ? null : cover.attr("data-embed"), "Server 1");
        int index = 2;
        for (Element button : document.select(".server-dropdown-item[data-server-embed]")) {
            String label = button.text().isBlank() ? "Server " + index : button.text().trim();
            addEmbed(out, button.attr("data-server-embed"), label);
            index++;
        }
        return List.copyOf(out.values());
    }

    private void addEmbed(Map<String,Models.Server> out, String rawHtml, String label) {
        if (rawHtml == null || rawHtml.isBlank()) return;
        Element iframe = Jsoup.parse(rawHtml).selectFirst("iframe[src]");
        if (iframe == null) return;
        String src = iframe.attr("src").trim();
        if (src.isBlank()) return;
        if (src.startsWith("//")) src = "https:" + src;
        out.putIfAbsent(src, new Models.Server(src, label, src));
    }

    private List<Models.ShowItem> parseApi(String json, String arrayKey, Models.ShowType type) {
        Map<String,Object> root = Json.object(Json.parse(json));
        List<Object> values = Json.array(root.get(arrayKey));
        ArrayList<Models.ShowItem> out = new ArrayList<>();
        for (Object raw : values) {
            Models.ShowItem item = toItem(Json.object(raw), type);
            if (item != null) out.add(item);
        }
        return out;
    }

    private Models.ShowItem toItem(Map<String,Object> v, Models.ShowType type) {
        String slug = Json.string(v.get("slug"));
        if (slug.isBlank()) slug = Json.string(v.get("slug_en"));
        String title = Json.string(v.get("title"));
        if (slug.isBlank() || title.isBlank()) return null;
        String released = optional(v.get("release_date"));
        Integer runtime = Json.integer(v.get("runtime"));
        Double rating = Json.decimal(v.get("vote_average"));
        return new Models.ShowItem(
                slug, slug, title, null, released, runtime, rating,
                absolute(optional(v.get("poster_path"))), null, type
        );
    }

    private Map<String,String> apiHeaders() {
        return Map.of("Platform", "android", "Accept", "application/json,text/plain,*/*");
    }

    private Document doc(String url) throws Exception {
        return Jsoup.parse(http.get(url, Map.of(
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language", "en-US,en;q=0.5",
                "Platform", "android"
        )), url);
    }

    private String absolute(String path) {
        if (path == null || path.isBlank()) return null;
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        return ROOT.substring(0, ROOT.length() - 1) + (path.startsWith("/") ? path : "/" + path);
    }

    private static String optional(Object value) {
        String s = Json.string(value);
        return s.isBlank() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static Integer numberAfter(String text, String marker) {
        if (text == null) return null;
        int at = text.lastIndexOf(marker);
        if (at < 0) return null;
        String tail = text.substring(at + marker.length());
        String digits = tail.replaceFirst("^(\\d+).*$", "$1");
        try { return Integer.parseInt(digits); }
        catch (Exception ignored) { return null; }
    }
}