package dev.streamflix.desktop;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

final class VoeExtractor implements Extractor {
    private static final Set<String> ALIASES = Set.of(
            "voe.sx", "jilliandescribecompany.com", "mikaylaarealike.com", "christopheruntilpoint.com",
            "walterprettytheir.com", "crystaltreatmenteast.com", "lauradaydo.com", "lancewhosedifficult.com",
            "dianaavoidthey.com", "jefferycontrolmodel.com", "charlestoughrace.com", "richardquestionbuilding.com",
            "jessicayeahcatch.com", "juliewomanwish.com", "rebeccapracticeloss.com", "johnbeyondnation.com"
    );
    private static final Map<String, String> HEADERS = Map.of(
            "User-Agent", Http.USER_AGENT,
            "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7",
            "X-Requested-With", "XMLHttpRequest"
    );
    private final Http http = new Http();

    @Override public String name() { return "VOE"; }
    @Override public boolean supports(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && (ALIASES.contains(host.toLowerCase(Locale.ROOT)) || host.toLowerCase(Locale.ROOT).contains("voe."));
        } catch (Exception ignored) { return false; }
    }

    @Override public Models.Video extract(String link) throws Exception {
        URI input = URI.create(link);
        String path = input.getRawPath() + (input.getRawQuery() == null ? "" : "?" + input.getRawQuery());
        Map<String,String> headers = new LinkedHashMap<>(HEADERS);
        headers.put("Referer", link);

        String first = http.get("https://voe.sx" + path, headers);
        Matcher redirect = Pattern.compile("https://([a-zA-Z0-9.-]+)(?:/[^'\"]*)?").matcher(first);
        if (!redirect.find()) throw new IllegalStateException("VOE redirect host not found");
        String finalBase = "https://" + redirect.group(1);
        String html = http.get(finalBase + path, headers);

        String encoded = findApplicationJson(html);
        if (encoded == null || encoded.isBlank()) throw new IllegalStateException("VOE encoded payload not found");
        Map<String,Object> decrypted = Json.object(Json.parse(decryptF7(encoded.trim())));
        String source = Json.string(decrypted.get("source"));
        if (source.isBlank()) throw new IllegalStateException("VOE source not found");

        String baseSubtitle = "";
        Matcher base = Pattern.compile("var\\s+base\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(html);
        if (base.find()) baseSubtitle = base.group(1);
        List<Models.Subtitle> subtitles = new ArrayList<>();
        for (Object raw : Json.array(decrypted.get("captions"))) {
            Map<String,Object> cap = Json.object(raw);
            String file = Json.string(cap.get("file"));
            if (!file.startsWith("http")) file = baseSubtitle + file;
            String label = Json.string(cap.get("label"));
            boolean def = Boolean.TRUE.equals(cap.get("default"));
            if (!file.isBlank()) subtitles.add(new Models.Subtitle(label, file, def));
        }
        return new Models.Video(source, Map.of("Referer", link, "User-Agent", Http.USER_AGENT), subtitles);
    }

    private static String findApplicationJson(String html) {
        Matcher m = Pattern.compile("<script[^>]*type=[\"']application/json[\"'][^>]*>(.*?)</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        while (m.find()) {
            String raw = m.group(1) == null ? "" : m.group(1).trim();
            if (raw.isBlank()) continue;
            if (raw.startsWith("[")) {
                List<Object> values = Json.array(Json.parse(raw));
                if (!values.isEmpty()) {
                    String value = Json.string(values.get(0));
                    if (!value.isBlank()) return value;
                }
                continue;
            }
            if (raw.startsWith("\"")) {
                String value = Json.string(Json.parse(raw));
                if (!value.isBlank()) return value;
            } else {
                return raw;
            }
        }
        return null;
    }

    static String decryptF7(String input) {
        String v = rot13(input);
        for (String pattern : List.of("@$", "^^", "~@", "%?", "*~", "!!", "#&")) v = v.replace(pattern, "_");
        v = v.replace("_", "");
        String stage1 = new String(Base64.getDecoder().decode(v), StandardCharsets.UTF_8);
        StringBuilder shifted = new StringBuilder(stage1.length());
        for (char c : stage1.toCharArray()) shifted.append((char) (c - 3));
        String reversed = shifted.reverse().toString();
        return new String(Base64.getDecoder().decode(reversed), StandardCharsets.UTF_8);
    }

    private static String rot13(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            if (c >= 'A' && c <= 'Z') out.append((char) ('A' + (c - 'A' + 13) % 26));
            else if (c >= 'a' && c <= 'z') out.append((char) ('a' + (c - 'a' + 13) % 26));
            else out.append(c);
        }
        return out.toString();
    }
}
