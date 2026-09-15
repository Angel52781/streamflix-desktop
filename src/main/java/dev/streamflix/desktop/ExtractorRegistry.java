package dev.streamflix.desktop;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;

final class ExtractorRegistry {
    private final List<Extractor> extractors = List.of(
            new DirectExtractor(),
            new FilemoonExtractor(),
            new VoeExtractor(),
            new StreamtapeExtractor()
    );

    Models.Video resolve(Models.Server server) throws Exception {
        String url = server.src() == null || server.src().isBlank() ? server.id() : server.src();
        for (Extractor extractor : extractors) {
            if (extractor.supports(url)) return extractor.extract(url);
        }
        throw new UnsupportedOperationException("Extractor aún no portado para: " + host(url));
    }

    static void openFallback(Models.Server server) throws Exception {
        String url = server.src() == null || server.src().isBlank() ? server.id() : server.src();
        if (!Desktop.isDesktopSupported()) throw new UnsupportedOperationException("Desktop browser no disponible");
        Desktop.getDesktop().browse(URI.create(url));
    }

    private static String host(String url) {
        try { return URI.create(url).getHost(); }
        catch (Exception ignored) { return url; }
    }

    private static final class DirectExtractor implements Extractor {
        @Override public String name() { return "Direct"; }
        @Override public boolean supports(String url) {
            String lower = url.toLowerCase();
            return lower.contains(".m3u8") || lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm");
        }
        @Override public Models.Video extract(String url) { return new Models.Video(url); }
    }
}
