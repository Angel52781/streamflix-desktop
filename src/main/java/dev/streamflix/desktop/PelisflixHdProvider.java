package dev.streamflix.desktop;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.util.*;
import java.util.regex.*;

final class PelisflixHdProvider implements Provider {
    private static final String ENTRY = "https://pelisflixhd.win/";
    private final Http http = new Http();
    private volatile String activeBase;

    @Override public String name() { return "PelisflixHD"; }

    private String base() throws Exception {
        String cached = activeBase;
        if (cached != null) return cached;
        synchronized (this) {
            if (activeBase == null) {
                URI uri = http.finalUri(ENTRY, Map.of());
                activeBase = uri.getScheme() + "://" + uri.getHost();
            }
            return activeBase;
        }
    }

    private Document doc(String url) throws Exception {
        byte[] bytes = http.getBytes(url, Map.of("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"));
        return Jsoup.parse(new java.io.ByteArrayInputStream(bytes), null, url);
    }
    @Override public List<Models.ShowItem> movies(int page) throws Exception {
        String url = page <= 1 ? base() + "/peliculas" : base() + "/peliculas/page/" + page;
        return parseShows(doc(url), Models.ShowType.MOVIE);
    }

    @Override public List<Models.ShowItem> tvShows(int page) throws Exception {
        String url = page <= 1 ? base() + "/series" : base() + "/series/page/" + page;
        return parseShows(doc(url), Models.ShowType.TV_SHOW);
    }

    @Override public List<Models.ShowItem> search(String query, int page) throws Exception {
        if (query == null || query.isBlank()) return List.of();
        String root = base() + "/busqueda/" + Http.encode(query);
        String url = page <= 1 ? root : root + "/page/" + page;
        return parseShows(doc(url), null);
    }

    private List<Models.ShowItem> parseShows(Document document, Models.ShowType forced) throws Exception {
        LinkedHashMap<String, Models.ShowItem> out = new LinkedHashMap<>();
        for (Element link : document.select("a[href*='/pelicula/'], a[href*='/serie/']")) {
            String href = normalize(link.attr("href"));
            if (href.isBlank()) continue;
            Models.ShowType type = forced != null ? forced : (href.contains("/pelicula/") ? Models.ShowType.MOVIE : Models.ShowType.TV_SHOW);
            if (type == Models.ShowType.MOVIE && !href.contains("/pelicula/")) continue;
            if (type == Models.ShowType.TV_SHOW && !href.contains("/serie/")) continue;
            String title = text(link.selectFirst(".item-detail p"));
            if (title == null) title = attr(link.selectFirst("img"), "alt");
            if (title == null || title.isBlank()) continue;
            title = title.replaceFirst("(?i)^Poster\\s+", "").trim();
            String poster = normalize(attr(link.selectFirst("img.poster, img"), "src"));
            String released = text(link.selectFirst(".card-hover-year, .item-picture .year"));
            Integer runtime = parseRuntime(text(link.selectFirst(".card-hover-info-meta > div")));
            String overview = text(link.selectFirst(".card-hover-info-overview p"));
            Models.ShowItem item = new Models.ShowItem(
                    href, href, title, overview, released, runtime, null,
                    poster, null, type);
            out.putIfAbsent(href, item);
        }
        return new ArrayList<>(out.values());
    }

    @Override public List<Models.Episode> episodes(Models.ShowItem show) throws Exception {
        if (show.type() != Models.ShowType.TV_SHOW) return List.of();
        Document detail = doc(show.providerId());
        LinkedHashMap<String,Integer> seasons = new LinkedHashMap<>();
        for (Element link : detail.select("a[href*='/temporada/']")) {
            String href = normalize(link.attr("href"));
            Integer season = numberFromSeason(href, link.text());
            if (season != null) seasons.putIfAbsent(href, season);
        }
        ArrayList<Models.Episode> out = new ArrayList<>();
        for (var entry : seasons.entrySet()) {
            Document seasonDoc = doc(entry.getKey());
            for (Element link : seasonDoc.select("a[href*='/episodio/']")) {
                String href = normalize(link.attr("href"));
                String code = text(link.select("span").size() > 1 ? link.select("span").get(1) : null);
                Integer episode = code == null ? null : regexInt(code, "(?i)x(\\d+)");
                if (episode == null) episode = regexInt(href, "(?i)(?:episodio|episode)[^0-9]*(\\d+)");
                if (episode == null) continue;
                String title = text(link.select("span").isEmpty() ? null : link.select("span").get(0));
                if (title == null || title.isBlank()) title = "Episodio " + episode;
                String poster = normalize(attr(link.selectFirst("img"), "src"));
                out.add(new Models.Episode(href, entry.getValue(), episode, title, null, poster));
            }
        }
        out.sort(Comparator.comparingInt(Models.Episode::seasonNumber).thenComparingInt(Models.Episode::episodeNumber));
        return out;
    }

    @Override public List<Models.Server> servers(String providerItemId) throws Exception {
        Document document = doc(normalize(providerItemId));
        ArrayList<Models.Server> out = new ArrayList<>();
        int index = 1;
        for (Element item : document.select("#player li[data-server]")) {
            String encoded = item.attr("data-server").trim();
            if (encoded.isBlank()) continue;
            try {
                String decoded = new String(Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (decoded.startsWith("//")) decoded = "https:" + decoded;
                if (!decoded.startsWith("http")) continue;
                String name = text(item.selectFirst("span"));
                if (name == null || name.isBlank()) name = "Opción " + index;
                out.add(new Models.Server(decoded, name, decoded));
                index++;
            } catch (Exception ignored) {}
        }
        return out.stream().distinct().toList();
    }
    private String normalize(String url) throws Exception {
        if (url == null) return "";
        String value = url.trim();
        if (value.isBlank()) return "";
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        if (value.startsWith("//")) return "https:" + value;
        return base() + (value.startsWith("/") ? value : "/" + value);
    }

    private static String text(Element e) {
        if (e == null) return null;
        String s = e.text().trim();
        return s.isBlank() ? null : s;
    }

    private static String attr(Element e, String name) {
        if (e == null) return null;
        String s = e.attr(name).trim();
        return s.isBlank() ? null : s;
    }

    private static Integer regexInt(String text, String regex) {
        if (text == null) return null;
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }
    private static Integer numberFromSeason(String href, String label) {
        Integer n = regexInt(href, "-(\\d+)/?$");
        if (n != null) return n;
        return regexInt(label, "(?i)temporada\\s+(\\d+)");
    }

    private static Integer parseRuntime(String text) {
        if (text == null) return null;
        Integer h = regexInt(text, "(?i)(\\d+)h");
        Integer m = regexInt(text, "(?i)(\\d+)min");
        int total = (h == null ? 0 : h * 60) + (m == null ? 0 : m);
        return total == 0 ? null : total;
    }
}
