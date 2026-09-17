package dev.streamflix.desktop;

import java.util.List;

/** Network-backed inventory for catalog breadth; does not start mpv. */
public final class ProviderInventoryLiveTest {
    public static void main(String[] args) {
        for (Provider provider : ProviderRegistry.all()) {
            try {
                if (provider instanceof TmdbProvider && !TmdbSettings.hasApiKey()) {
                    System.out.println(provider.name() + " CONFIG_REQUIRED");
                    continue;
                }
                List<Models.ShowItem> first = provider.supportsMovies()
                        ? provider.movies(1) : provider.tvShows(1);
                List<Models.ShowItem> second = provider.supportsMovies()
                        ? provider.movies(2) : provider.tvShows(2);
                String query = first.isEmpty() ? "a" : searchToken(first.get(0).title());
                List<Models.ShowItem> search = provider.search(query, 1);
                System.out.println(provider.name()
                        + " PAGE1=" + first.size()
                        + " PAGE2=" + second.size()
                        + " SEARCH=" + search.size()
                        + " QUERY=" + query);
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                System.out.println(provider.name() + " FAIL=" + message.replace('\n', ' '));
            }
        }
    }

    private static String searchToken(String title) {
        if (title == null || title.isBlank()) return "a";
        for (String word : title.replaceAll("[^\\p{L}\\p{N} ]", " ").split("\\s+")) {
            if (word.length() >= 4) return word;
        }
        return title.strip();
    }
}
