package dev.streamflix.desktop;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

final class NuuploadExtractor implements Extractor {
    private final Http http = new Http();
    @Override public String name() { return "Nuupload"; }
    @Override public boolean supports(String url) {
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        return u.contains("nupload.top/") || u.contains("nupupload.top/") || u.contains("nupload.me/") || u.contains("nupload.my/");
    }

    @Override public Models.Video extract(String url) throws Exception {
        URI finalUri = http.finalUri(url, Map.of("Referer", "https://nupload.top/"));
        String finalUrl = finalUri.toString();
        String html = http.get(finalUrl, Map.of("Referer", "https://nupload.top/"));
        Document doc = Jsoup.parse(html, finalUrl);

        String delegated = delegated(doc, finalUrl);
        if (delegated != null) throw new Delegated(delegated);

        String source = obfuscated(html);
        if (source == null) source = direct(html, finalUrl);
        if (source == null) throw new IllegalStateException("Nuupload: no se encontró un stream reproducible");
        URI sourceUri = http.finalUri(source, Map.of(
                "Referer", origin(finalUrl) + "/",
                "Origin", origin(finalUrl),
                "Accept", "*/*"
        ));
        return new Models.Video(sourceUri.toString(), Map.of(
                "Referer", origin(finalUrl) + "/",
                "Origin", origin(finalUrl)
        ), subtitles(doc, finalUrl));
    }

    private static String obfuscated(String html) {
        Matcher off = Pattern.compile("String\\.fromCharCode\\(parseInt\\(atob\\(value\\)\\.replace\\(/\\\\D/g,''\\)\\)\\s*-\\s*(\\d+)\\)")
                .matcher(html);
        if (!off.find()) return null;
        int offset = Integer.parseInt(off.group(1));
        Matcher array = Pattern.compile("var\\s+\\w+\\s*=\\s*\\[((?:\"[^\"]*\"\\s*,?\\s*)+)\\]\\s*;", Pattern.DOTALL)
                .matcher(html);
        if (!array.find()) return null;
        StringBuilder base = new StringBuilder();
        Matcher part = Pattern.compile("\"([^\"]+)\"").matcher(array.group(1));
        while (part.find()) {
            try {
                String decoded = new String(Base64.getDecoder().decode(part.group(1)), StandardCharsets.UTF_8);
                String digits = decoded.replaceAll("\\D", "");
                if (!digits.isBlank()) base.append((char) (Long.parseLong(digits) - offset));
            } catch (Exception ignored) {}
        }
        if (!base.toString().startsWith("http")) return null;
        Matcher session = Pattern.compile("var\\s+sesz\\s*=\\s*[\"']([^\"']+)[\"']").matcher(html);
        String source = base.toString();
        if (session.find()) source += (source.contains("?") ? "&" : "?") + "s=" + session.group(1);
        return source;
    }

    private static String direct(String html, String pageUrl) {
        for (String regex : List.of(
                "[\"']file[\"']\\s*[:=]\\s*[\"']((?:https?://|/)[^\"']+)[\"']",
                "https?://[^\"'\\s<]+\\.m3u8[^\"'\\s<]*",
                "https?://[^\"'\\s<]+\\.mp4[^\"'\\s<]*")) {
            Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(html);
            if (m.find()) {
                String v = m.groupCount() > 0 ? m.group(1) : m.group();
                return absolute(v, pageUrl);
            }
        }
        return null;
    }

    private static String delegated(Document doc, String pageUrl) {
        Element iframe = doc.selectFirst("iframe[src]");
        if (iframe == null) return null;
        String value = absolute(iframe.attr("src"), pageUrl);
        if (value == null) return null;
        String host = URI.create(value).getHost();
        if (host == null || host.contains("nupload")) return null;
        return value;
    }
    private static List<Models.Subtitle> subtitles(Document doc, String pageUrl) {
        ArrayList<Models.Subtitle> out = new ArrayList<>();
        for (Element track : doc.select("track[src]")) {
            String src = absolute(track.attr("src"), pageUrl);
            if (src == null || src.isBlank()) continue;
            String label = track.attr("label").isBlank() ? track.attr("srclang") : track.attr("label");
            if (label.isBlank()) label = "Subtitle";
            out.add(new Models.Subtitle(label, src, track.hasAttr("default")));
        }
        return out;
    }

    private static String absolute(String value, String pageUrl) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        if (v.startsWith("http://") || v.startsWith("https://")) return v;
        URI page = URI.create(pageUrl);
        if (v.startsWith("//")) return page.getScheme() + ":" + v;
        if (v.startsWith("/")) return page.getScheme() + "://" + page.getHost() + v;
        return v;
    }

    private static String origin(String url) {
        URI u = URI.create(url);
        return u.getScheme() + "://" + u.getHost();
    }

    static final class Delegated extends Exception {
        final String url;
        Delegated(String url) { super("Nuupload delegado a " + url); this.url = url; }
    }
}
