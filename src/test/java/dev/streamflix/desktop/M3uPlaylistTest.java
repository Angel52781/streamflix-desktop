package dev.streamflix.desktop;

import java.util.List;

public final class M3uPlaylistTest {
    public static void main(String[] args) {
        parsesMetadataAndVlcOptions();
        parsesPipeHeadersAndResetsState();
        ignoresMalformedEntries();
        System.out.println("M3uPlaylistTest OK");
    }

    private static void parsesMetadataAndVlcOptions() {
        String fixture = """
                #EXTM3U
                #EXTINF:-1 tvg-logo="https://img/a.png" group-title="News",Canal Uno
                #EXTVLCOPT:http-user-agent=Desktop UA
                #EXTVLCOPT:http-referrer=https://example.test/
                https://cdn.example.test/live/master.m3u8
                """;
        List<M3uPlaylist.Channel> channels = M3uPlaylist.parse(fixture);
        require(channels.size() == 1, "metadata count");
        M3uPlaylist.Channel channel = channels.get(0);
        require("Canal Uno".equals(channel.name()), "name");
        require("News".equals(channel.group()), "group");
        require("Desktop UA".equals(channel.userAgent()), "user agent");
        require("https://example.test/".equals(channel.referrer()), "referrer");
    }

    private static void parsesPipeHeadersAndResetsState() {
        String fixture = """
                #EXTINF:-1 http-user-agent="Attribute UA",Primero
                https://one.test/a.m3u8|User-Agent=Pipe%20UA&Referer=https%3A%2F%2Fref.test%2F
                #EXTINF:-1,Segundo
                https://two.test/b.m3u8
                """;
        List<M3uPlaylist.Channel> channels = M3uPlaylist.parse(fixture);
        require(channels.size() == 2, "pipe count");
        require("Pipe UA".equals(channels.get(0).userAgent()), "pipe UA overrides metadata");
        require("https://ref.test/".equals(channels.get(0).referrer()), "pipe referer");
        require(channels.get(1).userAgent() == null, "state reset UA");
        require(channels.get(1).referrer() == null, "state reset referrer");
    }

    private static void ignoresMalformedEntries() {
        String fixture = """
                https://orphan.test/master.m3u8
                #EXTINF:-1,Valid
                not-a-stream
                #EXTINF:-1,Still Valid
                https://valid.test/master.m3u8
                """;
        List<M3uPlaylist.Channel> channels = M3uPlaylist.parse(fixture);
        require(channels.size() == 1, "malformed count");
        require("Still Valid".equals(channels.get(0).name()), "malformed recovery");
    }

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError("Failed: " + name);
    }
}
