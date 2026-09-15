package dev.streamflix.desktop;

import java.net.URI;
import java.util.*;
import java.util.regex.*;

final class StreamtapeExtractor implements Extractor {
    private static final Pattern SCRIPT = Pattern.compile("document\\.getElementById\\('botlink'\\)\\.innerHTML\\s*=\\s*'([^']+)'\\s*\\+\\s*\\('([^']+)'\\)\\.substring\\(([0-9]+)\\)");
    private final Http http = new Http();

    @Override public String name() { return "Streamtape"; }
    @Override public boolean supports(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && (host.contains("streamtape") || host.contains("streamta.site"));
        } catch (Exception ignored) { return false; }
    }

    @Override public Models.Video extract(String link) throws Exception {
        URI uri = URI.create(link);
        String origin = uri.getScheme() + "://" + uri.getHost();
        String path = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        String html = http.get(origin + path, Map.of("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"));
        Matcher m = SCRIPT.matcher(html);
        if (!m.find()) throw new IllegalStateException("Streamtape botlink JavaScript not found");
        String baseUrl = decodeHtml(m.group(1));
        String params = decodeHtml(m.group(2));
        int cut = Integer.parseInt(m.group(3));
        if (cut > params.length()) throw new IllegalStateException("Streamtape substring index invalid");
        String clean = params.substring(cut);
        String id = param(clean, "id");
        String expires = param(clean, "expires");
        String ip = param(clean, "ip");
        String token = param(clean, "token");
        String finalVideo = (baseUrl.startsWith("http") ? baseUrl : origin + baseUrl)
                + (baseUrl.contains("?") ? "&" : "?")
                + "id=" + id + "&expires=" + expires + "&ip=" + ip + "&token=" + token + "&stream=1";
        URI finalUri = http.finalUri(finalVideo, Map.of("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"));
        return new Models.Video(finalUri.toString());
    }

    private static String param(String value, String name) {
        Matcher m = Pattern.compile("(?:^|&)" + Pattern.quote(name) + "=([^&]+)").matcher(value);
        if (!m.find()) throw new IllegalStateException("Streamtape " + name + " not found");
        return m.group(1);
    }
    private static String decodeHtml(String s) {
        return s.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"");
    }
}
