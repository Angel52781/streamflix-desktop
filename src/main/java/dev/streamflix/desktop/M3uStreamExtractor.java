package dev.streamflix.desktop;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class M3uStreamExtractor implements Extractor {
    static final String SCHEME = "streamflix-m3u:";

    @Override public String name() { return "M3U Direct"; }

    @Override public boolean supports(String url) {
        return url != null && url.startsWith(SCHEME);
    }

    @Override public Models.Video extract(String url) {
        M3uLiveProvider.ChannelSource source = M3uLiveProvider.decode(url.substring(SCHEME.length()));
        if (source.url() == null || source.url().isBlank()) {
            throw new IllegalStateException("El canal no contiene una URL reproducible.");
        }
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        if (source.userAgent() != null) headers.put("User-Agent", source.userAgent());
        if (source.referrer() != null) headers.put("Referer", source.referrer());
        return new Models.Video(source.url(), Map.copyOf(headers), List.of());
    }
}
