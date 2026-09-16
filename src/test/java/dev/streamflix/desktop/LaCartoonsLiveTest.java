package dev.streamflix.desktop;

public final class LaCartoonsLiveTest {
    public static void main(String[] args) throws Exception {
        Provider p = new LaCartoonsProvider();
        var shows = p.tvShows(1);
        System.out.println("SHOWS=" + shows.size());
        if (shows.isEmpty()) throw new IllegalStateException("LaCartoons empty");
        var first = shows.get(0);
        var poster = ImageLoader.download(first.poster());
        System.out.println("FIRST=" + first.title() + " POSTER=" + poster.getWidth() + "x" + poster.getHeight());
        var episodes = p.episodes(first);
        System.out.println("EPISODES=" + episodes.size());
        if (episodes.isEmpty()) throw new IllegalStateException("LaCartoons episodes empty");
        var registry = new ExtractorRegistry();
        int ok = 0;
        for (var ep : episodes) {
            var servers = p.servers(ep.id());
            System.out.println("EP=" + ep.episodeNumber() + " SERVERS=" + servers.size());
            for (var s : servers) {
                try {
                    var video = registry.resolve(s);
                    int code = MpvPlayer.smoke(video);
                    System.out.println(s.name() + " => " + code + " " + s.src());
                    if (code == 0) { ok++; break; }
                } catch (Exception ex) {
                    System.out.println(s.name() + " => FAIL " + ex.getMessage());
                }
            }
            if (ok > 0) break;
        }
        if (ok == 0) throw new IllegalStateException("LaCartoons no playable server");
        System.out.println("LACARTOONS_OK");
    }
}
