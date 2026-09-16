package dev.streamflix.desktop;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;
import java.util.regex.*;

final class AnimeSaturnProvider implements Provider {
    private static final String ROOT = "https://www.animesaturn.net";
    private final Http http = new Http();

    @Override public String id() { return "animesaturn"; }
    @Override public String name() { return "AnimeSaturn"; }
    @Override public boolean supportsMovies() { return false; }
    @Override public boolean supportsTvShows() { return true; }

    @Override public List<Models.ShowItem> movies(int page) { return List.of(); }

    @Override public List<Models.ShowItem> tvShows(int page) throws Exception {
        String url = (page <= 1) ? ROOT + "/filter" : ROOT + "/filter/" + page;
        Document doc = Jsoup.parse(http.get(url, headers()), url);
        return parseCards(doc);
    }

    @Override public List<Models.ShowItem> search(String query, int page) throws Exception {
        if (query == null || query.isBlank()) return List.of();
        String enc = Http.encode(query.trim());
        String url = (page <= 1) ? ROOT + "/filter?key=" + enc : ROOT + "/filter/" + page + "?key=" + enc;
        Document doc = Jsoup.parse(http.get(url, headers()), url);
        return parseCards(doc);
    }

    private List<Models.ShowItem> parseCards(Document doc) {
        ArrayList<Models.ShowItem> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Element a : doc.select("a.ac")) {
            String href = a.attr("href");
            String slug = slugFromHref(href);
            if (slug == null || !seen.add(slug)) continue;

            Element titleEl = a.selectFirst(".ac__title");
            String title = (titleEl != null && !titleEl.text().isBlank()) ? titleEl.text().trim() : "";
            Element img = a.selectFirst(".ac__poster img");
            if (title.isBlank() && img != null) {
                title = img.attr("alt").trim();
            }
            if (title.isBlank()) title = slug;

            String poster = img != null ? absolute(img.attr("src")) : null;
            out.add(new Models.ShowItem(slug, "show:" + slug, title, null, null, null, null, poster, null, Models.ShowType.TV_SHOW));
        }
        return out;
    }

    @Override public List<Models.Episode> episodes(Models.ShowItem show) throws Exception {
        String slug = show.providerId().replaceFirst("^show:", "");
        String url = ROOT + "/anime/" + slug;
        Document doc = Jsoup.parse(http.get(url, headers()), url);

        LinkedHashMap<Integer, Models.Episode> out = new LinkedHashMap<>();
        for (Element a : doc.select("a.ep-tile")) {
            String href = a.attr("href");
            int num = parseEpisodeNumber(href, out.size() + 1);
            String title = a.attr("title").isBlank() ? a.text().trim() : a.attr("title").trim();
            if (title.isBlank()) title = "Episodio " + num;
            String playerPath = toPlayerPath(href);
            out.putIfAbsent(num, new Models.Episode("episode:" + playerPath, 1, num, title, null, null));
        }
        return out.values().stream().sorted(Comparator.comparingInt(Models.Episode::episodeNumber)).toList();
    }

    @Override public List<Models.Server> servers(String providerItemId) throws Exception {
        String playerPath;
        if (providerItemId.startsWith("episode:")) {
            playerPath = providerItemId.substring("episode:".length());
        } else {
            String slug = providerItemId.replaceFirst("^show:", "");
            String url = ROOT + "/anime/" + slug;
            Document doc = Jsoup.parse(http.get(url, headers()), url);
            Element first = doc.selectFirst("a.ep-tile");
            if (first == null) return List.of();
            playerPath = toPlayerPath(first.attr("href"));
        }

        if (!playerPath.startsWith("/")) playerPath = "/" + playerPath;
        String playerUrl = ROOT + playerPath;
        String html = http.get(playerUrl, headers());

        return parseServersFromHtml(html);
    }

    static List<Models.Server> parseServersFromHtml(String html) {
        Matcher matcher = Pattern.compile("x-data=\"watchPage\\((.*?)\\)\"\\s*>", Pattern.DOTALL).matcher(html);
        if (!matcher.find()) return List.of();

        String rawJson = matcher.group(1).replace("&quot;", "\"").replace("&#039;", "'");
        try {
            Map<String, Object> data = Json.object(Json.parse(rawJson));
            List<Object> serversList = Json.array(data.get("servers"));
            List<Models.Server> out = new ArrayList<>();
            for (Object obj : serversList) {
                Map<String, Object> s = Json.object(obj);
                String link = Json.string(s.get("link"));
                if (link.isBlank()) continue;
                String name = Json.string(s.get("name"));
                if (name.isBlank()) name = "AnimeSaturn";
                out.add(new Models.Server(link, name, link));
            }
            if (!out.isEmpty()) return out;

            String init = Json.string(data.get("initialVideoUrl"));
            if (!init.isBlank()) {
                return List.of(new Models.Server(init, "AnimeSaturn", init));
            }
        } catch (Exception ignored) {}
        return List.of();
    }

    private static String slugFromHref(String href) {
        if (href == null || href.isBlank()) return null;
        Matcher m = Pattern.compile("/(?:anime|episode)/([^/?#]+)").matcher(href);
        return m.find() ? m.group(1) : null;
    }

    private static String toPlayerPath(String href) {
        return href.replaceFirst("^(?:https?://[^/]+)?/episode/", "/anime/");
    }

    private static int parseEpisodeNumber(String href, int fallback) {
        Matcher m = Pattern.compile("/ep-(\\d+)").matcher(href);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); }
            catch (Exception ignored) {}
        }
        return fallback;
    }

    private static String absolute(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        if (v.startsWith("http://") || v.startsWith("https://")) return v;
        if (v.startsWith("//")) return "https:" + v;
        return ROOT + (v.startsWith("/") ? v : "/" + v);
    }

    private static Map<String, String> headers() {
        return Map.of(
                "User-Agent", Http.USER_AGENT,
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        );
    }
}
