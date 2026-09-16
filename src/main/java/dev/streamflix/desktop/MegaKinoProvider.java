package dev.streamflix.desktop;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;
import java.util.regex.*;

final class MegaKinoProvider implements Provider {
    private static final String ROOT = "https://megakino19.com";
    private final Http http = new Http();
    private long lastTokenTime = 0L;

    @Override public String id() { return "megakino"; }
    @Override public String name() { return "MEGAKino"; }
    @Override public boolean supportsMovies() { return true; }
    @Override public boolean supportsTvShows() { return true; }

    @Override public List<Models.ShowItem> movies(int page) throws Exception {
        ensureToken();
        String url = (page <= 1) ? ROOT + "/films/" : ROOT + "/films/page/" + page + "/";
        Document doc = Jsoup.parse(http.get(url, headers()), ROOT);
        return parseItems(doc, Models.ShowType.MOVIE);
    }

    @Override public List<Models.ShowItem> tvShows(int page) throws Exception {
        ensureToken();
        String url = (page <= 1) ? ROOT + "/serials/" : ROOT + "/serials/page/" + page + "/";
        Document doc = Jsoup.parse(http.get(url, headers()), ROOT);
        return parseItems(doc, Models.ShowType.TV_SHOW);
    }

    @Override public List<Models.ShowItem> search(String query, int page) throws Exception {
        if (query == null || query.isBlank()) return List.of();
        ensureToken();
        int resultFrom = (page - 1) * 20 + 1;
        Map<String, String> form = Map.of(
                "do", "search",
                "subaction", "search",
                "search_start", String.valueOf(page),
                "full_search", "0",
                "result_from", String.valueOf(resultFrom),
                "story", query.trim()
        );
        String html = http.postForm(ROOT + "/index.php?do=search", form, headers());
        Document doc = Jsoup.parse(html, ROOT);
        return parseItems(doc, null);
    }

    private List<Models.ShowItem> parseItems(Document doc, Models.ShowType forcedType) {
        ArrayList<Models.ShowItem> out = new ArrayList<>();
        for (Element a : doc.select("div#dle-content a.poster.grid-item")) {
            String href = a.attr("href");
            if (href.isBlank()) continue;
            String title = a.select("h3.poster__title").text().trim();
            if (title.isBlank()) continue;

            Element img = a.selectFirst("div.poster__img img");
            String poster = null;
            if (img != null) {
                String src = img.attr("data-src").isBlank() ? img.attr("src") : img.attr("data-src");
                poster = absolute(src);
            }

            Models.ShowType type = forcedType != null ? forcedType : (href.contains("/serials/") ? Models.ShowType.TV_SHOW : Models.ShowType.MOVIE);
            String id = href.replaceFirst("^https?://[^/]+", "");
            out.add(new Models.ShowItem(id, "item:" + id, title, null, null, null, null, poster, null, type));
        }
        return out;
    }

    @Override public List<Models.Episode> episodes(Models.ShowItem show) throws Exception {
        ensureToken();
        String path = show.providerId().replaceFirst("^item:", "");
        String url = ROOT + path;
        Document doc = Jsoup.parse(http.get(url, headers()), ROOT);

        List<Models.Episode> out = new ArrayList<>();
        List<Element> options = doc.select("select.se-select option");
        if (!options.isEmpty()) {
            for (Element opt : options) {
                String val = opt.attr("value").trim();
                String text = opt.text().trim();
                if (val.isBlank()) continue;
                int num = parseEpisodeNumber(text, out.size() + 1);
                out.add(new Models.Episode("ep:" + path + "|" + val, 1, num, text, null, null));
            }
        } else {
            out.add(new Models.Episode("ep:" + path + "|film_main", 1, 1, "Episode 1", null, null));
        }
        return out;
    }

    @Override public List<Models.Server> servers(String providerItemId) throws Exception {
        ensureToken();
        List<Models.Server> out = new ArrayList<>();

        if (providerItemId.startsWith("ep:")) {
            String payload = providerItemId.substring("ep:".length());
            String[] parts = payload.split("\\|");
            String path = parts[0];
            String selectId = parts.length > 1 ? parts[1] : "";

            Document doc = Jsoup.parse(http.get(ROOT + path, headers()), ROOT);
            if (!selectId.isBlank() && !selectId.equals("film_main")) {
                Element select = doc.selectFirst("select#" + selectId);
                if (select != null) {
                    for (Element opt : select.select("option")) {
                        String serverUrl = opt.attr("value").trim();
                        String name = opt.text().trim();
                        if (!serverUrl.isBlank()) {
                            out.add(new Models.Server(serverUrl, name.isBlank() ? "Server" : name, serverUrl));
                        }
                    }
                }
            }
            if (out.isEmpty()) {
                extractTabIframes(doc, out);
            }
        } else {
            String path = providerItemId.replaceFirst("^item:", "");
            Document doc = Jsoup.parse(http.get(ROOT + path, headers()), ROOT);
            extractTabIframes(doc, out);
        }

        return out;
    }

    private void extractTabIframes(Document doc, List<Models.Server> out) {
        List<String> tabNames = doc.select("div.tabs-block__select span").eachText();
        List<Element> contents = doc.select("div.tabs-block__content");
        for (int i = 0; i < contents.size(); i++) {
            Element c = contents.get(i);
            Element iframe = c.selectFirst("iframe");
            if (iframe == null) continue;
            String src = iframe.attr("data-src").isBlank() ? iframe.attr("src") : iframe.attr("data-src");
            src = src.trim();
            if (src.isBlank() || src.contains("youtube.com")) continue;
            if (src.startsWith("//")) src = "https:" + src;
            String name = (i < tabNames.size() && !tabNames.get(i).isBlank()) ? tabNames.get(i) : "Server " + (i + 1);
            out.add(new Models.Server(src, name, src));
        }
        if (out.isEmpty()) {
            for (Element iframe : doc.select("iframe")) {
                String src = iframe.attr("data-src").isBlank() ? iframe.attr("src") : iframe.attr("data-src");
                src = src.trim();
                if (src.isBlank() || src.contains("youtube.com")) continue;
                if (src.startsWith("//")) src = "https:" + src;
                out.add(new Models.Server(src, "Server", src));
            }
        }
    }

    private synchronized void ensureToken() throws Exception {
        if (System.currentTimeMillis() - lastTokenTime > 10 * 60 * 1000) {
            try {
                http.get(ROOT + "/index.php?yg=token", headers());
                lastTokenTime = System.currentTimeMillis();
            } catch (Exception ignored) {}
        }
    }

    private static String absolute(String path) {
        if (path == null || path.isBlank()) return null;
        path = path.trim();
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        if (path.startsWith("//")) return "https:" + path;
        return ROOT + (path.startsWith("/") ? path : "/" + path);
    }

    private static int parseEpisodeNumber(String text, int fallback) {
        Matcher m = Pattern.compile("Episode\\s+(\\d+)").matcher(text);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); }
            catch (Exception ignored) {}
        }
        return fallback;
    }

    private static Map<String, String> headers() {
        return Map.of(
                "User-Agent", Http.USER_AGENT,
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        );
    }
}
