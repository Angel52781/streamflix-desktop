package dev.streamflix.desktop;

public final class SeriesTurcasLiveTest {
    public static void main(String[] args) throws Exception {
        Provider p = new SeriesTurcasProvider();
        var shows = p.tvShows(1);
        System.out.println("SHOWS=" + shows.size());
        if (shows.isEmpty()) throw new IllegalStateException("SeriesTurcas catalog empty");
        var poster = ImageLoader.download(shows.get(0).poster());
        System.out.println("FIRST=" + shows.get(0).title() + " POSTER=" + poster.getWidth() + "x" + poster.getHeight());
        var episodes = p.episodes(shows.get(0));
        System.out.println("EPISODES=" + episodes.size());
        if (episodes.isEmpty()) throw new IllegalStateException("SeriesTurcas episodes empty");
        var servers = p.servers(episodes.get(0).id());
        System.out.println("SERVERS=" + servers.size());
        if (servers.isEmpty()) throw new IllegalStateException("SeriesTurcas servers empty");
        var registry = new ExtractorRegistry();
        int playable = 0;
        for (var server : servers) {
            try {
                int code = MpvPlayer.smoke(registry.resolve(server));
                System.out.println(server.name() + " => " + code + " " + server.src());
                if (code == 0) { playable++; break; }
            } catch (Exception ex) {
                System.out.println(server.name() + " => FAIL " + ex.getMessage());
            }
        }
        if (playable == 0) throw new IllegalStateException("SeriesTurcas no playable server");
        System.out.println("SERIESTURCAS_OK");
    }
}
