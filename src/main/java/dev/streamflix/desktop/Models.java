package dev.streamflix.desktop;

import java.util.List;
import java.util.Map;

final class Models {
    private Models() {}

    enum ShowType { MOVIE, TV_SHOW }

    record ShowItem(
            String id,
            int providerId,
            String title,
            String overview,
            String released,
            Integer runtimeMinutes,
            Double rating,
            String poster,
            String banner,
            ShowType type
    ) {}

    record Episode(
            int id,
            int seasonNumber,
            int episodeNumber,
            String title,
            String overview,
            String poster
    ) {}

    record Server(String id, String name, String src) {}

    record Subtitle(String label, String file, boolean isDefault) {}

    record Video(
            String source,
            Map<String, String> headers,
            List<Subtitle> subtitles
    ) {
        Video(String source) {
            this(source, Map.of(), List.of());
        }
    }
}
