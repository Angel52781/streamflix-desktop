package dev.streamflix.desktop;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;

final class ExtractorRegistry {
    private static final int MAX_DELEGATION_DEPTH = 4;
    private final List<Extractor> extractors = List.of(
            new M3uStreamExtractor(),
            new DirectExtractor(),
            new VixSrcExtractor(),
            new EsprinahyExtractor(),
            new SaturnExtractor(),
            new VixcloudExtractor(),
            new VoeExtractor(),
            new DoodStreamExtractor(),
            new FilemoonExtractor(),
            new StreamtapeExtractor(),
            new NuuploadExtractor(),
            new OkruExtractor(),
            new CloseloadExtractor(),
            new VidMolyExtractor(),
            new GoodstreamExtractor()
    );

    Models.Video resolve(Models.Server server) throws Exception {
        return resolve(server, 0);
    }

    private Models.Video resolve(Models.Server server, int depth) throws Exception {
        if (depth > MAX_DELEGATION_DEPTH) {
            throw new IllegalStateException("Demasiadas redirecciones entre extractores");
        }
        String url = sourceUrl(server);
        for (Extractor extractor : extractors) {
            if (!extractor.supports(url)) continue;
            try {
                return extractor.extract(url);
            } catch (NuuploadExtractor.Delegated delegated) {
                if (delegated.url == null || delegated.url.equalsIgnoreCase(url)) throw delegated;
                Models.Server nested = new Models.Server(
                        delegated.url, server.name() + " → delegado", delegated.url);
                return resolve(nested, depth + 1);
            }
        }
        throw new UnsupportedOperationException("Extractor aún no portado para: " + host(url));
    }

    static void openFallback(Models.Server server) throws Exception {
        String url = sourceUrl(server);
        if (!Desktop.isDesktopSupported()) {
            throw new UnsupportedOperationException("Navegador del sistema no disponible");
        }
        Desktop.getDesktop().browse(URI.create(url));
    }

    private static String sourceUrl(Models.Server server) {
        return server.src() == null || server.src().isBlank() ? server.id() : server.src();
    }

    private static String host(String url) {
        try { return URI.create(url).getHost(); }
        catch (Exception ignored) { return url; }
    }

    private static final class DirectExtractor implements Extractor {
        @Override public String name() { return "Direct"; }
        @Override public boolean supports(String url) {
            String lower = url.toLowerCase();
            return lower.contains(".m3u8") || lower.endsWith(".mp4") ||
                   lower.endsWith(".mkv") || lower.endsWith(".webm");
        }
        @Override public Models.Video extract(String url) { return new Models.Video(url); }
    }
}
