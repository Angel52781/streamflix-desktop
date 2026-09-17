package dev.streamflix.desktop;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EsprinahyExtractor implements Extractor {
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final List<Pattern> REDIRECTS = List.of(
            Pattern.compile("(?is)window\\.location(?:\\.href)?\\.replace\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)"),
            Pattern.compile("(?is)window\\.location(?:\\.href)?\\s*=\\s*['\"]([^'\"]+)['\"]"),
            Pattern.compile("(?is)location\\.replace\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)"),
            Pattern.compile("(?is)location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]"),
            Pattern.compile("(?is)<meta[^>]+http-equiv=['\"]refresh['\"][^>]+content=['\"][^'\"]*url=([^'\">]+)['\"]")
    );
    private static final List<Pattern> SOURCES = List.of(
            Pattern.compile("(?i)(?:file|src)\\s*[:=]\\s*['\"](https?://[^'\"]+\\.(?:m3u8|mp4)(?:\\?[^'\"]*)?)['\"]"),
            Pattern.compile("(?i)sources?\\s*[:=]\\s*\\[\\s*['\"](https?://[^'\"]+\\.(?:m3u8|mp4)(?:\\?[^'\"]*)?)['\"]"),
            Pattern.compile("(?i)['\"](https?://[^'\"]+\\.(?:m3u8|mp4)(?:\\?[^'\"]*)?)['\"]")
    );

    private final Http http = new Http();

    @Override public String name() { return "Esprinahy"; }

    @Override public boolean supports(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && host.toLowerCase(Locale.ROOT).contains("esprinahy.com");
        } catch (Exception ignored) { return false; }
    }

    @Override public Models.Video extract(String link) throws Exception {
        String current = link;
        String referer = "https://tbg.seriesturcastv.to/";
        String source = null;

        for (int hop = 0; hop < 5; hop++) {
            String html = http.get(current, Map.of("Referer", referer, "User-Agent", UA));
            source = findSource(html);
            if (source == null) {
                Document doc = Jsoup.parse(html, current);
                for (Element script : doc.select("script")) {
                    try {
                        String unpacked = new JsUnpacker(script.html()).unpack();
                        source = findSource(unpacked);
                        if (source != null) break;
                    } catch (Exception ignored) {}
                }
            }
            if (source != null) break;

            String redirect = findRedirect(html);
            if (redirect == null) {
                Document doc = Jsoup.parse(html, current);
                for (Element script : doc.select("script")) {
                    try {
                        String unpacked = new JsUnpacker(script.html()).unpack();
                        redirect = findRedirect(unpacked);
                        if (redirect != null) break;
                    } catch (Exception ignored) {}
                }
            }
            if (redirect == null) break;
            String next = URI.create(current).resolve(redirect).toString();
            referer = current;
            current = next;
        }

        if (source == null || source.isBlank()) throw new IllegalStateException("Esprinahy: no se encontró fuente reproducible");
        URI u = URI.create(current);
        String origin = u.getScheme() + "://" + u.getHost();
        return new Models.Video(source, Map.of(
                "Referer", origin + "/",
                "Origin", origin,
                "User-Agent", UA
        ), List.of());
    }

    private static String findSource(String text) {
        String normalized = normalize(text);
        for (Pattern pattern : SOURCES) {
            Matcher m = pattern.matcher(normalized);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private static String findRedirect(String text) {
        String normalized = normalize(text);
        for (Pattern pattern : REDIRECTS) {
            Matcher m = pattern.matcher(normalized);
            if (m.find() && !m.group(1).isBlank()) return m.group(1);
        }
        return null;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&");
    }
}
