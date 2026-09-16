package dev.streamflix.desktop;

import java.net.URI;
import java.util.*;
import java.util.regex.*;

final class VidMolyExtractor implements Extractor {
    private static final String REFERER = "https://vidmoly.to/";
    private final Http http = new Http();

    @Override public String name() { return "VidMoly"; }

    @Override public boolean supports(String url) {
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        return u.contains("vidmoly.me/") || u.contains("vidmoly.to/") ||
               u.contains("vidmoly.net/") || u.contains("vidmoly.org/");
    }

    @Override public Models.Video extract(String url) throws Exception {
        String target = normalize(url);
        String html = http.get(target, Map.of(
                "Referer", REFERER,
                "Accept", "text/html",
                "User-Agent", Http.USER_AGENT
        ));
        String source = findSource(html);
        if (source == null) throw new IllegalStateException("VidMoly: no se encontró HLS");
        return new Models.Video(source, Map.of(
                "Referer", REFERER,
                "User-Agent", Http.USER_AGENT
        ), List.of());
    }
    private static String normalize(String url) {
        try {
            URI u = URI.create(url);
            String host = u.getHost();
            if (host != null && !host.equalsIgnoreCase("vidmoly.to")) {
                return new URI(u.getScheme(), u.getUserInfo(), "vidmoly.to", u.getPort(),
                        u.getPath(), u.getQuery(), u.getFragment()).toString();
            }
        } catch (Exception ignored) {}
        return url.replace("vidmoly.me/", "vidmoly.to/")
                  .replace("vidmoly.net/", "vidmoly.to/")
                  .replace("vidmoly.org/", "vidmoly.to/");
    }

    private static String findSource(String html) {
        String normalized = html.replace("\\/", "/");
        for (String regex : List.of(
                "sources\\s*:\\s*\\[\\{\\s*file\\s*:\\s*['\"]([^'\"]+)['\"]",
                "file\\s*:\\s*['\"](https?://[^'\"]+\\.m3u8[^'\"]*)['\"]",
                "['\"](https?://[^'\"]+\\.m3u8[^'\"]*)['\"]"
        )) {
            Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(normalized);
            if (m.find()) return m.group(1);
        }
        return null;
    }
}
