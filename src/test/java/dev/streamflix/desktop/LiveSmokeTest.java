package dev.streamflix.desktop;
import java.util.*;
public final class LiveSmokeTest {
  public static void main(String[] args) throws Exception {
    FanpelisProvider p = new FanpelisProvider();
    var movies = p.movies(1);
    if (movies.isEmpty()) throw new IllegalStateException("No movies");
    System.out.println("MOVIES=" + movies.size() + " FIRST=" + movies.get(0).title());
    var servers = p.servers(movies.get(0).providerId());
    if (servers.isEmpty()) throw new IllegalStateException("No servers");
    System.out.println("SERVERS=" + servers.size());
    ExtractorRegistry r = new ExtractorRegistry();
    Exception last = null;
    for (var s : servers) {
      try {
        var v = r.resolve(s);
        System.out.println("RESOLVED=" + s.name() + " URL=" + v.source());
        if (v.source() == null || v.source().isBlank()) throw new IllegalStateException("Empty source");
        return;
      } catch (Exception e) { last = e; System.out.println("SKIP=" + s.name() + " :: " + e.getMessage()); }
    }
    throw new IllegalStateException("No server resolved", last);
  }
}
