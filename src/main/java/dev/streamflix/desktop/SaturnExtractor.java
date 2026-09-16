package dev.streamflix.desktop;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

final class SaturnExtractor implements Extractor {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private final Http http = new Http();

    @Override public String name() { return "AnimeSaturn"; }

    @Override public boolean supports(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            String lower = host.toLowerCase(Locale.ROOT);
            return (lower.contains("saturncdn.net") || lower.contains("animesaturn.")) && url.contains("/embed/");
        } catch (Exception ignored) { return false; }
    }

    @Override public Models.Video extract(String link) throws Exception {
        URI uri = URI.create(link);
        String origin = uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() > 0 && uri.getPort() != 80 && uri.getPort() != 443 ? ":" + uri.getPort() : "");

        Map<String, String> embedHeaders = Map.of(
                "User-Agent", USER_AGENT,
                "Referer", "https://www.animesaturn.net/"
        );
        String embedHtml = http.get(link, embedHeaders);

        Matcher m = Pattern.compile("window\\.__E\\s*=\\s*\\{\\s*i\\s*:\\s*(\\d+)\\s*,\\s*k\\s*:\\s*\"([^\"]+)\"\\s*,\\s*e\\s*:\\s*(\\d+)").matcher(embedHtml);
        String embedId;
        String token;
        String expires;

        if (m.find()) {
            embedId = m.group(1);
            token = m.group(2);
            expires = m.group(3);
        } else {
            Matcher pathMatcher = Pattern.compile("/embed/(\\d+)").matcher(uri.getPath());
            embedId = pathMatcher.find() ? pathMatcher.group(1) : "";
            Map<String, String> query = parseQuery(uri.getQuery());
            token = query.getOrDefault("token", "as");
            expires = query.getOrDefault("expires", "");
        }

        if (embedId.isBlank() || token.isBlank()) {
            throw new IllegalStateException("Saturn embed params not found for " + link);
        }

        String playlistUrl = origin + "/embed/" + Http.encode(embedId) + "/playlist?token="
                + Http.encode(token) + "&expires=" + Http.encode(expires);

        Map<String, String> playlistHeaders = Map.of(
                "User-Agent", USER_AGENT,
                "Referer", link,
                "X-Requested-With", "XMLHttpRequest",
                "Accept", "application/json, text/plain, */*"
        );

        String playlistBody = http.get(playlistUrl, playlistHeaders);
        Map<String, Object> root = Json.object(Json.parse(playlistBody));
        String payload = Json.string(root.get("d"));
        if (payload.isBlank()) throw new IllegalStateException("Saturn embed empty video payload");

        String videoUrl = decodePayload(payload, token);
        if (videoUrl.isBlank()) throw new IllegalStateException("Saturn embed decrypted empty source");
        if (videoUrl.startsWith("youtube/")) {
            throw new UnsupportedOperationException("Saturn embed is YouTube link");
        }

        Map<String, String> videoHeaders = Map.of(
                "User-Agent", USER_AGENT,
                "Referer", link
        );
        return new Models.Video(videoUrl, videoHeaders, List.of());
    }

    static String decodePayload(String payload, String key) {
        String k = (key == null || key.isEmpty()) ? "as" : key;
        byte[] bytes = Base64.getDecoder().decode(payload);
        char[] out = new char[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            out[i] = (char) ((bytes[i] & 0xFF) ^ k.charAt(i % k.length()));
        }
        return new String(out);
    }

    private static Map<String, String> parseQuery(String query) {
        if (query == null || query.isBlank()) return Map.of();
        Map<String, String> map = new HashMap<>();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                map.put(pair.substring(0, idx), URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
            }
        }
        return map;
    }
}
