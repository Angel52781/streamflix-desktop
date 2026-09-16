package dev.streamflix.desktop;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.*;
import java.util.regex.*;

final class OkruExtractor implements Extractor {
    private static final String ROOT = "https://ok.ru";
    private final Http http = new Http();

    @Override public String name() { return "Okru"; }
    @Override public boolean supports(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("ok.ru/") || u.contains("odnoklassniki.ru/");
    }

    @Override public Models.Video extract(String url) throws Exception {
        String html = http.get(url, Map.of("Referer", ROOT + "/", "User-Agent", Http.USER_AGENT));
        Document doc = Jsoup.parse(html, url);
        Element options = doc.selectFirst("div[data-options]");
        if (options == null) throw new IllegalStateException("Okru: data-options no encontrado");
        String raw = options.attr("data-options");
        String decoded = org.jsoup.parser.Parser.unescapeEntities(raw, false)
                .replace("\\u0026", "&").replace("\\/", "/").replace("\\\"", "\"");
        String source = bestVideo(decoded);
        if (source == null) throw new IllegalStateException("Okru: video no encontrado");
        return new Models.Video(source, Map.of(
                "Referer", ROOT,
                "User-Agent", Http.USER_AGENT
        ), List.of());
    }
    private static String bestVideo(String text) {
        Matcher hls = Pattern.compile("\\\"hlsManifestUrl\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", Pattern.CASE_INSENSITIVE).matcher(text);
        if (hls.find()) return hls.group(1).replace("\\u0026", "&").replace("\\/", "/");
        ArrayList<String> urls = new ArrayList<>();
        Matcher objects = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*?\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", Pattern.DOTALL).matcher(text);
        while (objects.find()) {
            String u = objects.group(2).replace("\\u0026", "&").replace("\\/", "/").replace("\\\"", "\"");
            if (u.startsWith("https://")) urls.add(u);
        }
        if (!urls.isEmpty()) return urls.get(urls.size() - 1);

        Matcher fallback = Pattern.compile("https://[^\\\"'\\s]+", Pattern.CASE_INSENSITIVE).matcher(text);
        while (fallback.find()) {
            String u = fallback.group();
            if (u.contains(".mp4") || u.contains("video")) urls.add(u);
        }
        return urls.isEmpty() ? null : urls.get(urls.size() - 1);
    }
}
