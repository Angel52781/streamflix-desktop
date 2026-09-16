package dev.streamflix.desktop;

import java.net.URI;
import java.util.*;
import java.util.regex.*;

final class VixcloudExtractor implements Extractor {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private final Http http = new Http();

    @Override public String name() { return "Vixcloud"; }

    @Override public boolean supports(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            String lower = host.toLowerCase(Locale.ROOT);
            return lower.contains("vixcloud.co") || lower.contains("vix-content.net");
        } catch (Exception ignored) { return false; }
    }

    @Override public Models.Video extract(String link) throws Exception {
        Map<String, String> headers = Map.of(
                "User-Agent", USER_AGENT,
                "Referer", "https://www.animeunity.so/"
        );
        String html = http.get(link, headers);

        // 1. Check window.downloadUrl for direct MP4 stream
        Matcher dl = Pattern.compile("window\\.downloadUrl\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(html);
        if (dl.find()) {
            String directUrl = dl.group(1);
            Map<String, String> videoHeaders = Map.of(
                    "User-Agent", USER_AGENT,
                    "Referer", link
            );
            return new Models.Video(directUrl, videoHeaders, List.of());
        }

        // 2. Fallback: Parse masterPlaylist and window.video
        Matcher idMatcher = Pattern.compile("window\\.video\\s*=\\s*\\{[^}]*id\\s*:\\s*['\"]?(\\d+)['\"]?").matcher(html);
        String videoId = idMatcher.find() ? idMatcher.group(1) : "";

        Matcher tokenMatcher = Pattern.compile("['\"]token['\"]\\s*:\\s*['\"]([^'\"]+)['\"]").matcher(html);
        String token = tokenMatcher.find() ? tokenMatcher.group(1) : "";

        Matcher expiresMatcher = Pattern.compile("['\"]expires['\"]\\s*:\\s*['\"]([^'\"]+)['\"]").matcher(html);
        String expires = expiresMatcher.find() ? expiresMatcher.group(1) : "";

        if (videoId.isBlank() || token.isBlank()) {
            throw new IllegalStateException("Vixcloud: stream parameters not found");
        }

        URI uri = URI.create(link);
        String playlistUrl = uri.getScheme() + "://" + uri.getHost() + "/playlist/" + videoId
                + "?token=" + Http.encode(token) + "&expires=" + Http.encode(expires) + "&b=1&h=1";

        Map<String, String> videoHeaders = Map.of(
                "User-Agent", USER_AGENT,
                "Referer", link
        );
        return new Models.Video(playlistUrl, videoHeaders, List.of());
    }
}
