package dev.streamflix.desktop;

import java.util.List;

public final class M3uLiveProviderTest {
    public static void main(String[] args) throws Exception {
        M3uPlaylist.Channel channel = new M3uPlaylist.Channel(
                "Canal Demo",
                "https://cdn.example.test/live/master.m3u8",
                "https://img.example.test/logo.png",
                "News",
                "Desktop UA",
                "https://ref.example.test/");
        String encoded = M3uLiveProvider.encode(channel);
        M3uLiveProvider.ChannelSource decoded = M3uLiveProvider.decode(encoded);
        require("https://cdn.example.test/live/master.m3u8".equals(decoded.url()), "URL roundtrip");
        require("Desktop UA".equals(decoded.userAgent()), "UA roundtrip");
        require("https://ref.example.test/".equals(decoded.referrer()), "referer roundtrip");

        Models.Video video = new M3uStreamExtractor().extract(M3uStreamExtractor.SCHEME + encoded);
        require(channel.url().equals(video.source()), "video source");
        require("Desktop UA".equals(video.headers().get("User-Agent")), "video UA");
        require("https://ref.example.test/".equals(video.headers().get("Referer")), "video referer");

        require(ProviderRegistry.get("iptv-spain") != null, "IPTV Spain registered");
        require(ProviderRegistry.get("pluto-mx") != null, "Pluto MX registered");
        require(ProviderRegistry.get("pluto-es") != null, "Pluto ES registered");
        require(ProviderRegistry.get("pluto-us") != null, "Pluto US registered");
        System.out.println("M3uLiveProviderTest OK");
    }

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError("Failed: " + name);
    }
}
