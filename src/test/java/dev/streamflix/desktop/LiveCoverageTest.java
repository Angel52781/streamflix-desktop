package dev.streamflix.desktop;

public final class LiveCoverageTest {
    public static void main(String[] args) throws Exception {
        FanpelisProvider provider = new FanpelisProvider();
        var movies = provider.movies(1);
        if (movies.isEmpty()) throw new IllegalStateException("No movies");
        var first = movies.get(0);
        System.out.println("ITEM=" + first.title());
        var servers = provider.servers(first.providerId());
        ExtractorRegistry registry = new ExtractorRegistry();
        int resolved = 0;
        for (var server : servers) {
            try {
                var video = registry.resolve(server);
                boolean ok = video.source() != null && !video.source().isBlank();
                System.out.println((ok ? "OK " : "EMPTY ") + server.name() + " => " + video.source());
                if (ok) resolved++;
            } catch (Exception e) {
                System.out.println("FAIL " + server.name() + " => " + e.getMessage());
            }
        }
        if (resolved == 0) throw new IllegalStateException("No server resolved");
        System.out.println("RESOLVED=" + resolved + "/" + servers.size());
    }
}
