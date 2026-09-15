package dev.streamflix.desktop;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ExtractorFixtureTest {
    public static void main(String[] args) {
        String json = "{\"source\":\"https://cdn.example/video.m3u8\",\"captions\":[]}";
        String encoded = encodeInverseF7(json);
        String decoded = VoeExtractor.decryptF7(encoded);
        require(json.equals(decoded), "VOE decrypt roundtrip");

        Extractor direct = new Extractor() {
            @Override public String name() { return "x"; }
            @Override public boolean supports(String url) { return url.endsWith(".m3u8"); }
            @Override public Models.Video extract(String url) { return new Models.Video(url); }
        };
        require(direct.supports("https://example.test/a.m3u8"), "direct fixture");
        require(new FilemoonExtractor().supports("https://filemoon.sx/e/abc123"), "filemoon host");
        require(new StreamtapeExtractor().supports("https://streamtape.com/e/abc"), "streamtape host");
        require(new VoeExtractor().supports("https://voe.sx/e/abc"), "voe host");
        System.out.println("ExtractorFixtureTest OK");
    }

    private static String encodeInverseF7(String json) {
        String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String reversed = new StringBuilder(b64).reverse().toString();
        StringBuilder shifted = new StringBuilder();
        for (char c : reversed.toCharArray()) shifted.append((char) (c + 3));
        String stage = Base64.getEncoder().encodeToString(shifted.toString().getBytes(StandardCharsets.UTF_8));
        return rot13(stage);
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

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError("Failed: " + name);
    }
}
