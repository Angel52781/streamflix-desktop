package dev.streamflix.desktop;

import java.util.List;

final class ProviderRegistry {
    private ProviderRegistry() {}

    static List<Provider> all() {
        return List.of(
                new FanpelisProvider(),
                new RidoMoviesProvider(),
                new PelisflixHdProvider(),
                new AnimeWorldProvider(),
                new SeriesTurcasProvider(),
                new LaCartoonsProvider()
        );
    }
}
