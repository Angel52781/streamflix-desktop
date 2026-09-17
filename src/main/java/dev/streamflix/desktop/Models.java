package dev.streamflix.desktop;

import java.util.List;
import java.util.Map;

final class Models {
    private Models() {}

    enum ShowType { MOVIE, TV_SHOW }

    record ShowItem(
            String id,
            String providerId,
            String title,
            String overview,
            String released,
            Integer runtimeMinutes,
            Double rating,
            String poster,
            String banner,
            ShowType type,
            String sourceProviderId
    ) {
        ShowItem(
                String id,
                String providerId,
                String title,
                String overview,
                String released,
                Integer runtimeMinutes,
                Double rating,
                String poster,
                String banner,
                ShowType type
        ) {
            this(id, providerId, title, overview, released, runtimeMinutes, rating, poster, banner, type, null);
        }

        ShowItem withSourceProviderId(String value) {
            return new ShowItem(id, providerId, title, overview, released, runtimeMinutes, rating, poster, banner, type, value);
        }
    }

    record Episode(
            String id,
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
