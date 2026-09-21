package dev.streamflix.desktop;

import java.time.Instant;
import java.util.List;

public final class SportsPlaybackTest {
    public static void main(String[] args) {
        SportsEvent event = new SportsEvent("1", "Fútbol", "Liga", "A vs B", "A", "B",
                null, null, 1, 0, "Live", "30'", Instant.now(),
                List.of(new SportsBroadcaster("ESPN", "Peru", null)));
        Models.ShowItem one = channel("ESPN 1080p", "https://example.test/one.m3u8");
        Models.ShowItem two = channel("ESPN 2", "https://example.test/two.m3u8");
        SportsBroadcaster broadcaster = event.broadcasters().get(0);
        SportsResolvedProvider provider = new SportsResolvedProvider(event, List.of(
                new SportsChannelCandidate(broadcaster, "iptv-all-world", "IPTV All World", one, 140),
                new SportsChannelCandidate(broadcaster, "iptv-all-world", "IPTV All World", two, 120)));
        List<Models.Server> servers = provider.servers(event.id());
        require(servers.size() == 2, "all fallback signals should become playback servers");
        require(servers.get(0).src().startsWith(M3uStreamExtractor.SCHEME), "sports servers must use M3U extractor");
        require(provider.item().type() == Models.ShowType.MOVIE, "event item should autoplay as a single live asset");

        SportsPlaybackPlan plan = new SportsPlaybackPlan(event, List.of(
                new SportsChannelHealth(new SportsChannelCandidate(broadcaster, "x", "X", one, 100), true, 100),
                new SportsChannelHealth(new SportsChannelCandidate(broadcaster, "x", "X", two, 90), false, 200)));
        require(plan.verifiedCandidates().size() == 1, "health plan should expose only verified signals");
        require(plan.allCandidates().size() == 2, "fallback plan should retain unverified candidates");
        System.out.println("SportsPlaybackTest OK");
    }

    private static Models.ShowItem channel(String name, String url) {
        M3uPlaylist.Channel channel = new M3uPlaylist.Channel(name, url, null, "Sports", null, null);
        String encoded = M3uLiveProvider.encode(channel);
        return new Models.ShowItem("iptv:" + encoded, encoded, name, "Canal en vivo", null,
                null, null, null, null, Models.ShowType.TV_SHOW, "iptv-all-world");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
