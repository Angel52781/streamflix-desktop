package dev.streamflix.desktop;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GoodstreamExtractor implements Extractor {
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private final Http http = new Http();

    @Override public String name() { return "Goodstream"; }

    @Override public boolean supports(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && host.toLowerCase(Locale.ROOT).contains("goodstream");
        } catch (Exception ignored) { return false; }
    }

    @Override public Models.Video extract(String link) throws Exception {
        Map<String,String> headers = Map.of("User-Agent", UA, "Accept-Language", "en-US,en;q=0.9");
        String html = http.get(link, headers);
        Matcher script = Pattern.compile("<script[^>]*>(.*?)</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        Pattern sourcePattern = Pattern.compile("file\\s*:\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        String source = null;
        while (script.find()) {
            String body = script.group(1);
            if (!body.contains("jwplayer") || !body.contains("sources") || !body.contains("file")) continue;
            Matcher file = sourcePattern.matcher(body);
            if (file.find()) {
                source = file.group(1);
                break;
            }
        }
        if (source == null || source.isBlank()) throw new IllegalStateException("Goodstream source not found");
        return new Models.Video(source, Map.of(
                "User-Agent", UA,
                "Accept-Language", "en-US,en;q=0.9",
                "Referer", "https://goodstream.one"
        ), java.util.List.of());
    }
}
