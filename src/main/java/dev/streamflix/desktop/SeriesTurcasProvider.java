package dev.streamflix.desktop;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.util.*;
import java.util.regex.*;

final class SeriesTurcasProvider implements Provider {
    private static final String ENTRY = "https://tbg.seriesturcastv.to/";
    private final Http http = new Http();
    private volatile String activeBase;

    @Override public String name() { return "Series Turcas"; }
    @Override public boolean supportsMovies() { return false; }

    private String base() throws Exception {
        if (activeBase != null) return activeBase;
        synchronized (this) {
            if (activeBase == null) {
                URI uri = http.finalUri(ENTRY, headers(ENTRY));
                activeBase = uri.getScheme() + "://" + uri.getHost();
            }
        }
        return activeBase;
    }

    private Map<String,String> headers(String referer) {
        return Map.of(
                "Referer", referer,
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                "Accept-Language", "es-ES,es;q=0.9,en;q=0.5"
        );
    }
    private Document doc(String url) throws Exception {
        byte[] bytes = http.getBytes(url, headers(base() + "/"));
        return Jsoup.parse(new java.io.ByteArrayInputStream(bytes), null, url);
    }

    @Override public List<Models.ShowItem> movies(int page) { return List.of(); }

    @Override public List<Models.ShowItem> tvShows(int page) throws Exception {
        if (page > 1) return List.of();
        return parseCards(doc(base() + "/series/"));
    }

    @Override public List<Models.ShowItem> search(String query, int page) throws Exception {
        if (query == null || query.isBlank()) return List.of();
        String url = page <= 1
                ? base() + "/?s=" + Http.encode(query)
                : base() + "/page/" + page + "/?s=" + Http.encode(query);
        return parseCards(doc(url));
    }

    private List<Models.ShowItem> parseCards(Document document) {
        LinkedHashMap<String,Models.ShowItem> out = new LinkedHashMap<>();
        for (Element item : document.select("#body .filmlist > .item, .filmlist > .item")) {
            Element link = item.selectFirst("a.poster[href], h3 a.title[href]");
            if (link == null) continue;
            String href = link.attr("href").trim();
            if (href.isBlank() || href.toLowerCase(Locale.ROOT).contains("capitulo-")) continue;
            String title = item.selectFirst("h3 a.title") == null ? link.attr("title") : item.selectFirst("h3 a.title").text();
            if (title == null || title.isBlank()) continue;
            Element img = item.selectFirst("a.poster img, img");
            String poster = img == null ? null : firstNonBlank(img.attr("data-src"), img.attr("src"));
            out.putIfAbsent(href, new Models.ShowItem(href, href, title.trim(), null, null, null, null, poster, null, Models.ShowType.TV_SHOW));
        }
        return new ArrayList<>(out.values());
    }
    @Override public List<Models.Episode> episodes(Models.ShowItem show) throws Exception {
        Document document = doc(show.providerId());
        ArrayList<Models.Episode> out = new ArrayList<>();
        for (Element a : document.select("#episodes a.episod[href]")) {
            String href = a.attr("href").trim();
            Integer number = number(a.ownText());
            if (number == null) number = number(a.text());
            if (number == null) number = number(href);
            if (href.isBlank() || number == null) continue;
            out.add(new Models.Episode(href, 1, number, "Capítulo " + number, null, show.poster()));
        }
        return out.stream().distinct().sorted(Comparator.comparingInt(Models.Episode::episodeNumber)).toList();
    }

    @Override public List<Models.Server> servers(String providerItemId) throws Exception {
        Document document = doc(providerItemId);
        ArrayList<Models.Server> out = new ArrayList<>();
        int index = 1;
        for (Element a : document.select(".dltabsi .dl-contenti a[href]")) {
            String url = normalizeServer(a.attr("href").trim());
            if (!url.startsWith("http")) continue;
            String host;
            try { host = URI.create(url).getHost(); } catch (Exception ex) { host = null; }
            String label = host == null ? "Servidor " + index : host.replaceFirst("^www\\.", "");
            out.add(new Models.Server(url, label, url));
            index++;
        }
        return out;
    }

    private String normalizeServer(String url) {
        if (url.contains("esprinahy.com/d/")) return url.replace("esprinahy.com/d/", "esprinahy.com/f/");
        Matcher vm = Pattern.compile("https?://(?:www\\.)?vidmoly\\.[^/]+/dl/([^/?#]+)", Pattern.CASE_INSENSITIVE).matcher(url);
        if (vm.find()) return "https://vidmoly.to/embed-" + vm.group(1);
        Matcher voe = Pattern.compile("(https?://[^/]*voe[^/]*/)([^/?#]+)/download", Pattern.CASE_INSENSITIVE).matcher(url);
        if (voe.find()) return voe.group(1) + "e/" + voe.group(2);
        return url;
    }
    private static Integer number(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("(?i)(?:capitulo|capítulo|episodio)[^0-9]*(\\d+)").matcher(text);
        if (!m.find()) return null;
        try { return Integer.parseInt(m.group(1)); }
        catch (Exception ignored) { return null; }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }
}
