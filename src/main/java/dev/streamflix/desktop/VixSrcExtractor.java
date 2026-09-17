package dev.streamflix.desktop;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Desktop JVM extractor for the public VixSrc TMDb endpoints. */
final class VixSrcExtractor implements Extractor {
    static final String MAIN_URL = "https://vixsrc.to";
    private static final String UA = Http.USER_AGENT;
    private static final Pattern VIDEO_ID = Pattern.compile(
            "window\\.video\\s*=\\s*\\{.*?\\bid\\s*:\\s*['\"]?([A-Za-z0-9_-]+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TOKEN = Pattern.compile(
            "['\"]token['\"]\\s*:\\s*['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPIRES = Pattern.compile(
            "['\"]expires['\"]\\s*:\\s*['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LANG = Pattern.compile("(?:^|[?&])lang=([^&#]+)", Pattern.CASE_INSENSITIVE);

    private final Http http;

    VixSrcExtractor() {
        this(new Http());
    }

    VixSrcExtractor(Http http) {
        this.http = http;
    }

    @Override public String name() {
        return "VixSrc";
    }

    @Override public boolean supports(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null && (host.equalsIgnoreCase("vixsrc.to") || host.endsWith(".vixsrc.to"));
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override public Models.Video extract(String link) throws Exception {
        if (!supports(link)) throw new IllegalArgumentException("VixSrc: unsupported URL");
        String lang = languageFrom(link);
        String embedUrl = link;

        URI input = URI.create(link);
        if (input.getPath() != null && input.getPath().startsWith("/api/")) {
            String apiUrl = withLanguage(link, lang);
            String apiJson = http.get(apiUrl, Map.of(
                    "Accept", "application/json, text/plain, */*",
                    "X-Requested-With", "XMLHttpRequest",
                    "Referer", MAIN_URL + "/",
                    "User-Agent", UA
            ));
            String src = Json.string(Json.object(Json.parse(apiJson)).get("src")).strip();
            if (src.isBlank()) throw new IllegalStateException("VixSrc: API response did not contain an embed source");
            embedUrl = resolve(MAIN_URL + "/", src);
        }

        String html = http.get(embedUrl, Map.of(
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language", lang.equals("es") ? "es-ES,es;q=0.9,en;q=0.7" : "en-US,en;q=0.9,es;q=0.7",
                "X-Requested-With", "XMLHttpRequest",
                "Referer", MAIN_URL + "/",
                "User-Agent", UA
        ));

        StreamParams params = parseStreamParams(html);
        StringBuilder playlist = new StringBuilder(MAIN_URL)
                .append("/playlist/").append(params.videoId())
                .append("?token=").append(Http.encode(params.token()))
                .append("&expires=").append(Http.encode(params.expires()));
        if (params.hasB()) playlist.append("&b=1");
        if (params.canPlayFhd()) playlist.append("&h=1");
        playlist.append("&lang=").append(Http.encode(lang));

        return new Models.Video(playlist.toString(), Map.of(
                "Referer", embedUrl,
                "User-Agent", UA
        ), List.of());
    }

    static StreamParams parseStreamParams(String html) {
        String videoId = first(VIDEO_ID, html);
        String token = first(TOKEN, html);
        String expires = first(EXPIRES, html);
        if (videoId.isBlank() || token.isBlank() || expires.isBlank()) {
            throw new IllegalStateException("VixSrc: stream parameters not found");
        }
        boolean hasB = Pattern.compile("[?&]b=1(?:[&'\"\\s]|$)", Pattern.CASE_INSENSITIVE)
                .matcher(html).find();
        boolean fhd = Pattern.compile("window\\.canPlayFHD\\s*=\\s*true", Pattern.CASE_INSENSITIVE)
                .matcher(html).find();
        return new StreamParams(videoId, token, expires, hasB, fhd);
    }

    private static String languageFrom(String url) {
        Matcher matcher = LANG.matcher(url);
        if (!matcher.find()) return "en";
        try {
            String decoded = java.net.URLDecoder.decode(matcher.group(1), java.nio.charset.StandardCharsets.UTF_8);
            return decoded.toLowerCase(Locale.ROOT).startsWith("es") ? "es" : "en";
        } catch (Exception ignored) {
            return "en";
        }
    }

    private static String withLanguage(String link, String lang) {
        if (LANG.matcher(link).find()) return link;
        return link + (link.contains("?") ? "&" : "?") + "lang=" + Http.encode(lang);
    }

    private static String resolve(String base, String relative) {
        URI resolved = URI.create(base).resolve(relative);
        if (!"https".equalsIgnoreCase(resolved.getScheme()) || resolved.getHost() == null
                || !(resolved.getHost().equalsIgnoreCase("vixsrc.to") || resolved.getHost().endsWith(".vixsrc.to"))) {
            throw new IllegalStateException("VixSrc: invalid embed source");
        }
        return resolved.toString();
    }

    private static String first(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(1).strip() : "";
    }

    record StreamParams(String videoId, String token, String expires, boolean hasB, boolean canPlayFhd) {}
}
