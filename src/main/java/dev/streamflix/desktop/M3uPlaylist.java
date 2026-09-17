package dev.streamflix.desktop;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class M3uPlaylist {
    private M3uPlaylist() {}

    record Channel(String name, String url, String logo, String group,
                   String userAgent, String referrer) {}

    static List<Channel> parse(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        ArrayList<Channel> channels = new ArrayList<>();
        Pending pending = new Pending();

        for (String line : raw.split("\\R")) {
            String value = line.trim();
            if (value.isEmpty()) continue;
            if (value.startsWith("#EXTINF")) {
                pending = parseExtInf(value);
            } else if (value.regionMatches(true, 0, "#EXTVLCOPT:", 0, 11)) {
                parseVlcOption(value.substring(11), pending);
            } else if (!value.startsWith("#") && looksLikeStream(value) && !pending.name.isBlank()) {
                StreamTarget target = parseStreamTarget(value);
                String ua = firstNonBlank(target.headers.get("user-agent"), pending.userAgent);
                String ref = firstNonBlank(target.headers.get("referer"), target.headers.get("referrer"), pending.referrer);
                channels.add(new Channel(pending.name, target.url,
                        blankToNull(pending.logo), blankToNull(pending.group),
                        blankToNull(ua), blankToNull(ref)));
                pending = new Pending();
            }
        }
        return List.copyOf(channels);
    }

    private static Pending parseExtInf(String line) {
        Pending pending = new Pending();
        int comma = findMetadataComma(line);
        String metadata = comma >= 0 ? line.substring(0, comma) : line;
        pending.name = comma >= 0 ? line.substring(comma + 1).trim() : "";
        Map<String, String> attrs = parseAttributes(metadata);
        pending.logo = attrs.getOrDefault("tvg-logo", "");
        pending.group = attrs.getOrDefault("group-title", "");
        pending.userAgent = firstNonBlank(attrs.get("http-user-agent"), attrs.get("user-agent"));
        pending.referrer = firstNonBlank(attrs.get("http-referrer"), attrs.get("http-referer"), attrs.get("referer"));
        return pending;
    }

    private static int findMetadataComma(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (c == ',' && !quoted) return i;
        }
        return -1;
    }

    private static Map<String, String> parseAttributes(String metadata) {
        LinkedHashMap<String, String> attrs = new LinkedHashMap<>();
        int i = 0;
        while (i < metadata.length()) {
            while (i < metadata.length() && !isKeyStart(metadata.charAt(i))) i++;
            int start = i;
            while (i < metadata.length() && isKeyChar(metadata.charAt(i))) i++;
            if (start == i || i >= metadata.length() || metadata.charAt(i) != '=') continue;
            String key = metadata.substring(start, i).toLowerCase(Locale.ROOT);
            i++;
            String value;
            if (i < metadata.length() && metadata.charAt(i) == '"') {
                int end = metadata.indexOf('"', ++i);
                if (end < 0) break;
                value = metadata.substring(i, end);
                i = end + 1;
            } else {
                int end = i;
                while (end < metadata.length() && !Character.isWhitespace(metadata.charAt(end))) end++;
                value = metadata.substring(i, end);
                i = end;
            }
            attrs.put(key, value);
        }
        return attrs;
    }

    private static void parseVlcOption(String option, Pending pending) {
        int equals = option.indexOf('=');
        if (equals <= 0) return;
        String key = option.substring(0, equals).trim().toLowerCase(Locale.ROOT);
        String value = option.substring(equals + 1).trim();
        if (key.equals("http-user-agent")) pending.userAgent = value;
        if (key.equals("http-referrer") || key.equals("http-referer")) pending.referrer = value;
    }

    private static StreamTarget parseStreamTarget(String value) {
        int pipe = value.indexOf('|');
        if (pipe < 0) return new StreamTarget(value, Map.of());
        String url = value.substring(0, pipe).trim();
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        String suffix = value.substring(pipe + 1);
        for (String part : suffix.split("&")) {
            int equals = part.indexOf('=');
            if (equals <= 0) continue;
            String key = decode(part.substring(0, equals)).trim().toLowerCase(Locale.ROOT);
            String headerValue = decode(part.substring(equals + 1)).trim();
            headers.put(key, headerValue);
        }
        return new StreamTarget(url, Map.copyOf(headers));
    }

    private static boolean looksLikeStream(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("rtmp://") || lower.startsWith("rtsp://");
    }

    private static boolean isKeyStart(char c) { return Character.isLetterOrDigit(c); }
    private static boolean isKeyChar(char c) { return Character.isLetterOrDigit(c) || c == '-' || c == '_'; }
    private static String decode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private static final class Pending {
        String name = "";
        String logo = "";
        String group = "";
        String userAgent;
        String referrer;
    }

    private record StreamTarget(String url, Map<String, String> headers) {}
}
