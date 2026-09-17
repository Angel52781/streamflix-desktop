package dev.streamflix.desktop;

import java.util.List;

public final class LiveTvProvidersLiveTest {
    public static void main(String[] args) throws Exception {
        for (String id : List.of("iptv-spain", "iptv-all-world", "pluto-mx", "pluto-es", "pluto-us")) {
            Provider raw = ProviderRegistry.get(id);
            if (!(raw instanceof M3uLiveProvider provider)) throw new IllegalStateException(id + ": provider missing");
            List<Models.ShowItem> page = provider.tvShows(1);
            if (page.isEmpty()) throw new IllegalStateException(provider.name() + ": empty playlist");
            System.out.println(provider.name() + ": channels=" + provider.channelCountForTest()
                    + " page1=" + page.size());

            Exception last = null;
            boolean played = false;
            for (Models.ShowItem item : page.stream().limit(8).toList()) {
                try {
                    List<Models.Server> servers = provider.servers(item.providerId());
                    if (servers.isEmpty()) continue;
                    Models.Video video = new ExtractorRegistry().resolve(servers.get(0));
                    int exit = MpvPlayer.smoke(video);
                    if (exit == 0) {
                        System.out.println(provider.name() + ": mpv OK via " + item.title());
                        played = true;
                        break;
                    }
                    last = new IllegalStateException("mpv exit=" + exit + " via " + item.title());
                } catch (Exception ex) {
                    last = ex;
                }
            }
            if (!played) throw new IllegalStateException(provider.name() + ": no playable sample", last);
        }
        System.out.println("LiveTvProvidersLiveTest OK");
    }
}
