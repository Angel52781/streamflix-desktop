package dev.streamflix.desktop;

public final class PelisflixHdLiveTest {
    public static void main(String[] args) throws Exception {
        Provider p = new PelisflixHdProvider();
        var movies = p.movies(1);
        System.out.println("MOVIES=" + movies.size());
        if (movies.isEmpty()) throw new IllegalStateException("Pelisflix movies empty");
        var poster = ImageLoader.download(movies.get(0).poster());
        System.out.println("FIRST=" + movies.get(0).title() + " POSTER=" + poster.getWidth() + "x" + poster.getHeight());
        var servers = p.servers(movies.get(0).providerId());
        System.out.println("MOVIE_SERVERS=" + servers.size());
        var registry = new ExtractorRegistry();
        int ok = 0;
        for (var s : servers) {
            try { int code = MpvPlayer.smoke(registry.resolve(s)); System.out.println(s.name()+" => "+code+" "+s.src()); if(code==0){ok++; break;} }
            catch(Exception e){ System.out.println(s.name()+" => FAIL "+e.getMessage()); }
        }
        if (ok == 0) throw new IllegalStateException("No playable Pelisflix movie server");
        System.out.println("PELISFLIX_OK");
    }
}
