package dev.streamflix.desktop;

public final class ProviderSearchLiveTest {
    public static void main(String[] args) throws Exception {
        for (Provider p : ProviderRegistry.all()) {
            var movies = p.movies(1);
            var shows = movies.isEmpty() ? p.tvShows(1) : movies;
            if (shows.isEmpty()) throw new IllegalStateException(p.name() + " catalog empty");
            String title = shows.get(0).title();
            String query = pickWord(title);
            var found = p.search(query, 1);
            System.out.println(p.name() + " query='" + query + "' results=" + found.size());
            if (found.isEmpty()) throw new IllegalStateException(p.name() + " search returned 0 for " + query);
        }
        System.out.println("SEARCH_ALL_OK");
    }

    private static String pickWord(String title) {
        for (String word : title.replaceAll("[^\\p{L}\\p{N} ]", " ").split("\\s+")) {
            if (word.length() >= 4) return word;
        }
        return title.split("\\s+")[0];
    }
}
