package dev.streamflix.desktop;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

final class CloseloadExtractor implements Extractor {
    private final Http http = new Http();
    @Override public String name() { return "Closeload"; }
    @Override public boolean supports(String url) {
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        return u.contains("closeload.top/") || u.contains("ridorapid.closeload.top/");
    }

    @Override public Models.Video extract(String url) throws Exception {
        String html = http.get(url, Map.of("Referer", "https://ridomovies.su/"));
        StringBuilder search = new StringBuilder(html);
        for (Element script : Jsoup.parse(html, url).select("script")) {
            String js = script.data().isBlank() ? script.html() : script.data();
            if (!js.contains("eval(function")) continue;
            JsUnpacker unpacker = new JsUnpacker(js);
            if (unpacker.detect()) {
                String unpacked = unpacker.unpack();
                if (unpacked != null) search.append('\n').append(unpacked);
            }
        }
        String source = decodeCurrent(search.toString());
        if (source == null || source.isBlank()) throw new IllegalStateException("Closeload: no se pudo descifrar el stream actual");
        URI uri = URI.create(url);
        String referer = uri.getScheme() + "://" + uri.getHost() + "/";
        return new Models.Video(source, Map.of("Referer", referer), subtitles(html));
    }

    private static String decodeCurrent(String html) {
        Pattern assignment = Pattern.compile("var\\s+(\\w+)\\s*=\\s*(\\w+)\\s*\\(\\s*\\[((?:\"[^\"]+\",?\\s*)+)\\]\\s*\\)\\s*;");
        Matcher assignments = assignment.matcher(html);
        while (assignments.find()) {
            String functionName = assignments.group(2);
            String partsText = assignments.group(3);
            Pattern function = Pattern.compile("function\\s+" + Pattern.quote(functionName) + "\\s*\\([^)]*\\)\\s*\\{(.*?)return\\s+\\w+\\s*;?\\s*\\}", Pattern.DOTALL);
            Matcher fm = function.matcher(html);
            if (!fm.find()) continue;
            String body = fm.group(1);
            if (!body.contains("65521") || !body.contains("charCodeAt")) continue;

            Matcher constants = Pattern.compile("var\\s+\\w+\\s*=\\s*\"([^\"]+)\"\\s*;\\s*var\\s+\\w+\\s*=\\s*\"([^\"]+)\"", Pattern.DOTALL).matcher(body);
            if (!constants.find()) continue;
            String key = constants.group(1);
            String ops = constants.group(2);

            StringBuilder joined = new StringBuilder();
            Matcher pm = Pattern.compile("\"([^\"]+)\"").matcher(partsText);
            int partCount = 0;
            while (pm.find()) { joined.append(pm.group(1)); partCount++; }
            if (partCount == 0) continue;

            try {
                String decoded = decodePayload(joined.toString().replace("\\/", "/"), key, ops, partCount);
                if (decoded != null && decoded.trim().startsWith("http")) return decoded.trim();
            } catch (Exception ignored) {}
        }
        return null;
    }
    private static String decodePayload(String encoded, String key, String ops, int partCount) {
        int hash = 0, mix = 0;
        for (int i = 0; i < key.length(); i++) {
            int c = key.charAt(i);
            hash = (hash * 31 + c) % 251;
            mix = (mix ^ (c + i)) & 255;
        }
        int seed = (hash + mix) % 256;
        int step = (hash % 13) + 3;
        int perm = ((hash * 256 + mix) % 65521) + 1;
        String value = encoded;

        for (int i = ops.length() - 1; i >= 0; i--) {
            char op = ops.charAt(i);
            if (op == 'b') value = new String(decodeBase64(value), StandardCharsets.ISO_8859_1);
            else if (op == 'v') value = new StringBuilder(value).reverse().toString();
            else {
                int rot = (26 - ((op - 64) % 26)) % 26;
                value = rotate(value, rot);
            }
        }
        int length = value.length();
        int[] swaps = new int[length];
        for (int i = length - 1; i >= 1; i--) {
            perm = (perm * 75 + 74) % 65537;
            swaps[i] = perm % (i + 1);
        }
        char[] chars = value.toCharArray();
        for (int i = 1; i < length; i++) {
            int j = swaps[i];
            char tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp;
        }

        int acc = seed;
        StringBuilder out = new StringBuilder(length);
        for (char ch : chars) {
            int c = ch & 255;
            acc = (acc + step) % 256;
            out.append((char) (c ^ acc));
            acc = (acc + c) % 256;
        }
        return out.toString();
    }
    private static byte[] decodeBase64(String value) {
        String clean = value.replaceAll("\\s+", "");
        int mod = clean.length() % 4;
        if (mod != 0) clean += "=".repeat(4 - mod);
        return Base64.getDecoder().decode(clean);
    }

    private static String rotate(String value, int offset) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (c >= 'a' && c <= 'z') out.append((char) ('a' + (c - 'a' + offset) % 26));
            else if (c >= 'A' && c <= 'Z') out.append((char) ('A' + (c - 'A' + offset) % 26));
            else out.append(c);
        }
        return out.toString();
    }

    private static List<Models.Subtitle> subtitles(String html) {
        ArrayList<Models.Subtitle> out = new ArrayList<>();
        Matcher tracks = Pattern.compile("\\{\\\"file\\\":\\\"([^\\\"]+)\\\",\\\"kind\\\":\\\"captions\\\",\\\"label\\\":\\\"([^\\\"]+)\\\",\\\"default\\\":(true|false)\\}").matcher(html);
        while (tracks.find()) out.add(new Models.Subtitle(tracks.group(2), tracks.group(1).replace("\\/", "/"), Boolean.parseBoolean(tracks.group(3))));
        return out;
    }
}
