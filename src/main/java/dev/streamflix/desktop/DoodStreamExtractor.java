package dev.streamflix.desktop;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DoodStreamExtractor implements Extractor {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Http http = new Http();

    @Override public String name() { return "DoodStream"; }

    @Override public boolean supports(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            String h = host.toLowerCase(Locale.ROOT);
            return h.contains("dood") || h.equals("d000d.com") || h.equals("dsvplay.com")
                    || h.equals("myvidplay.com") || h.equals("playmogo.com") || h.equals("do7go.com");
        } catch (Exception ignored) { return false; }
    }
    @Override public Models.Video extract(String link) throws Exception {
        String embedUrl = link.replace("/d/", "/e/");
        Map<String,String> initialHeaders = Map.of("Referer", link);
        URI finalUri = http.finalUri(embedUrl, initialHeaders);
        String finalUrl = finalUri.toString();
        String html = http.get(finalUrl, initialHeaders);
        String base = finalUri.getScheme() + "://" + finalUri.getHost();

        Matcher md5 = Pattern.compile("/pass_md5/[^'\"<\\s]*").matcher(html);
        if (!md5.find()) throw new IllegalStateException("DoodStream pass_md5 path not found");
        String md5Url = base + md5.group();
        String prefix = http.get(md5Url, Map.of("Referer", finalUrl)).trim();
        if (prefix.isBlank()) throw new IllegalStateException("DoodStream video prefix missing");

        String token = md5Url.substring(md5Url.lastIndexOf('/') + 1);
        String source = prefix + randomSuffix(10) + "?token=" + token;
        return new Models.Video(source, Map.of("Referer", base), java.util.List.of());
    }

    private static String randomSuffix(int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) out.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return out.toString();
    }
}
