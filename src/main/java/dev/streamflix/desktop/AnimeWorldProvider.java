package dev.streamflix.desktop;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.*;

final class AnimeWorldProvider implements Provider {
    private static final String ROOT = "https://www.animeworld.ac";
    private final Http http = new Http();

    @Override public String name() { return "AnimeWorld"; }

    @Override public List<Models.ShowItem> movies(int page) throws Exception {
        return listing(ROOT + "/filter?type=4&sort=0&page=" + page, Models.ShowType.MOVIE);
    }

    @Override public List<Models.ShowItem> tvShows(int page) throws Exception {
        return listing(ROOT + "/filter?type=0&sort=0&page=" + page, Models.ShowType.TV_SHOW);
    }

    @Override public List<Models.ShowItem> search(String query, int page) throws Exception {
        if (query == null || query.isBlank()) return List.of();
        Document doc = doc(ROOT + "/search?keyword=" + Http.encode(query) + "&page=" + page);
        ArrayList<Models.ShowItem> out = new ArrayList<>();
        for (Element item : doc.select("div.film-list .item")) {
            Models.ShowType type = item.selectFirst(".status .movie") != null ? Models.ShowType.MOVIE : Models.ShowType.TV_SHOW;
            Models.ShowItem parsed = parseItem(item, type);
            if (parsed != null) out.add(parsed);
        }
        return out;
    }
    private List<Models.ShowItem> listing(String url, Models.ShowType type) throws Exception {
        Document doc = doc(url);
        ArrayList<Models.ShowItem> out = new ArrayList<>();
        for (Element item : doc.select("div.film-list .item")) {
            Models.ShowItem parsed = parseItem(item, type);
            if (parsed != null) out.add(parsed);
        }
        return out;
    }

    private Models.ShowItem parseItem(Element item, Models.ShowType type) {
        Element link = item.selectFirst("a.name[href*='/play/']");
        if (link == null) return null;
        String href = link.attr("href");
        int marker = href.indexOf("/play/");
        String slug = marker >= 0 ? href.substring(marker + 6).replaceFirst("^/+", "") : href.replaceFirst("^/+", "");
        if (slug.isBlank()) return null;
        String title = link.text().trim();
        String poster = absolute(item.selectFirst("img") == null ? null : item.selectFirst("img").attr("src"));
        return new Models.ShowItem(slug, "show:" + slug, title, null, null, null, null, poster, null, type);
    }

    @Override public List<Models.Episode> episodes(Models.ShowItem show) throws Exception {
        if (show.type() != Models.ShowType.TV_SHOW) return List.of();
        String slug = show.providerId().replaceFirst("^show:", "");
        Document doc = doc(ROOT + "/play/" + slug);
        LinkedHashMap<Integer, Models.Episode> out = new LinkedHashMap<>();
        for (Element a : episodeAnchors(doc)) {
            int number = parseInt(a.attr("data-episode-num"), parseInt(a.text(), 0));
            String code = a.attr("data-id").trim();
            if (number <= 0 || code.isBlank()) continue;
            out.putIfAbsent(number, new Models.Episode("episode:" + code, 1, number, "Episodio " + number, null, null));
        }
        return out.values().stream().sorted(Comparator.comparingInt(Models.Episode::episodeNumber)).toList();
    }
    @Override public List<Models.Server> servers(String providerItemId) throws Exception {
        String code;
        if (providerItemId.startsWith("episode:")) {
            code = providerItemId.substring("episode:".length());
        } else {
            String slug = providerItemId.replaceFirst("^show:", "");
            Document doc = doc(ROOT + "/play/" + slug);
            Element first = episodeAnchors(doc).stream().findFirst().orElse(null);
            if (first == null) return List.of();
            code = first.attr("data-id").trim();
        }
        if (code.isBlank()) return List.of();
        String api = ROOT + "/api/episode/info?id=" + Http.encode(code) + "&alt=0";
        Map<String,Object> root = Json.object(Json.parse(http.get(api, headers())));
        String grabber = Json.string(root.get("grabber"));
        if (grabber.isBlank()) return List.of();
        if (grabber.startsWith("//")) grabber = "https:" + grabber;
        return List.of(new Models.Server(grabber, "AnimeWorld", grabber));
    }

    private List<Element> episodeAnchors(Document doc) {
        List<Element> primary = doc.select("div.server[data-id=9] a[data-episode-id][data-id]");
        if (!primary.isEmpty()) return primary;
        return doc.select("div.server[data-id=8] a[data-episode-id][data-id]");
    }

    private Document doc(String url) throws Exception {
        byte[] bytes = http.getBytes(url, headers());
        return Jsoup.parse(new java.io.ByteArrayInputStream(bytes), null, url);
    }

    private static Map<String,String> headers() {
        return Map.of("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
    }
    private static String absolute(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        if (v.startsWith("http://") || v.startsWith("https://")) return v;
        if (v.startsWith("//")) return "https:" + v;
        return ROOT + (v.startsWith("/") ? v : "/" + v);
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); }
        catch (Exception ignored) { return fallback; }
    }
}
