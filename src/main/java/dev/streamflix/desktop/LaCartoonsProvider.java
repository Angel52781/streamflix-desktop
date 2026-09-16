package dev.streamflix.desktop;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.*;

final class LaCartoonsProvider implements Provider {
    private static final String ROOT = "https://www.lacartoons.com";
    private final Http http = new Http();

    @Override public String name() { return "La Cartoons"; }
    @Override public boolean supportsMovies() { return false; }
    @Override public List<Models.ShowItem> movies(int page) { return List.of(); }

    @Override public List<Models.ShowItem> tvShows(int page) throws Exception {
        String url = page <= 1 ? ROOT : ROOT + "/?page=" + page;
        return parseShows(doc(url));
    }

    @Override public List<Models.ShowItem> search(String query, int page) throws Exception {
        if (query == null || query.isBlank() || page > 1) return List.of();
        return parseShows(doc(ROOT + "/?Titulo=" + Http.encode(query)));
    }

    private List<Models.ShowItem> parseShows(Document document) {
        LinkedHashMap<String,Models.ShowItem> out = new LinkedHashMap<>();
        for (Element a : document.select("div.conjuntos-series a[href*='/serie/']")) {
            Element card = a.selectFirst("div.serie");
            if (card == null) continue;
            String href = absolute(a.attr("href"));
            String title = text(card.selectFirst("p.nombre-serie"));
            if (title == null) title = a.attr("title").trim();
            if (href.isBlank() || title == null || title.isBlank()) continue;
            Element img = card.selectFirst("img");
            String poster = img == null ? null : absolute(img.attr("src"));
            out.putIfAbsent(href, new Models.ShowItem(
                    href, href, title, null, null, null, null,
                    poster, poster, Models.ShowType.TV_SHOW));
        }
        return new ArrayList<>(out.values());
    }

    @Override public List<Models.Episode> episodes(Models.ShowItem show) throws Exception {
        Document document = doc(absolute(show.providerId()));
        ArrayList<Models.Episode> out = new ArrayList<>();
        List<Element> panels = document.select("section.contenedor-episodio-temporada div.episodio-panel");
        if (!panels.isEmpty()) {
            for (int i = 0; i < panels.size(); i++) {
                int season = i + 1;
                for (Element a : panels.get(i).select("ul.listas-de-episodion li a[href]")) {
                    addEpisode(out, a, season);
                }
            }
        } else {
            for (Element a : document.select("ul.listas-de-episodion li a[href]")) addEpisode(out, a, 1);
        }
        out.sort(Comparator.comparingInt(Models.Episode::seasonNumber).thenComparingInt(Models.Episode::episodeNumber));
        return out;
    }

    private void addEpisode(List<Models.Episode> out, Element a, int season) {
        String label = a.text().trim();
        int number = episodeNumber(label);
        if (number <= 0) return;
        String href = absolute(a.attr("href"));
        out.add(new Models.Episode(href, season, number, label, null, null));
    }
    @Override public List<Models.Server> servers(String providerItemId) throws Exception {
        Document document = doc(absolute(providerItemId));
        LinkedHashMap<String,Models.Server> out = new LinkedHashMap<>();
        for (Element iframe : document.select("iframe[src]")) {
            String src = absolute(iframe.attr("src"));
            if (src.isBlank()) continue;
            String host;
            try { host = java.net.URI.create(src).getHost(); }
            catch (Exception ex) { host = null; }
            String name = host == null ? "Servidor" : host.replaceFirst("^www\\.", "").split("\\.")[0];
            out.putIfAbsent(src, new Models.Server(src, name, src));
        }
        return new ArrayList<>(out.values());
    }

    private Document doc(String url) throws Exception {
        return Jsoup.parse(http.get(url, Map.of(
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Referer", ROOT + "/"
        )), url);
    }

    private static String absolute(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.isBlank()) return "";
        if (v.startsWith("http://") || v.startsWith("https://")) return v;
        if (v.startsWith("//")) return "https:" + v;
        return ROOT + (v.startsWith("/") ? v : "/" + v);
    }
    private static String text(Element e) {
        if (e == null) return null;
        String s = e.text().trim();
        return s.isBlank() ? null : s;
    }

    private static int episodeNumber(String label) {
        if (label == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)cap(?:i|í)tulo\\s*(\\d+)").matcher(label);
        if (!m.find()) return 0;
        try { return Integer.parseInt(m.group(1)); }
        catch (Exception ignored) { return 0; }
    }
}
