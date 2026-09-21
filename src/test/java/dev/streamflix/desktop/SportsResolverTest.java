package dev.streamflix.desktop;

import java.util.List;

public final class SportsResolverTest {
    public static void main(String[] args) {
        require(SportsChannelResolver.nameScore("ESPN", "ESPN HD") >= 90,
                "ESPN should match ESPN HD");
        require(SportsChannelResolver.nameScore("ESPN", "ESPN HD")
                        > SportsChannelResolver.nameScore("ESPN", "ESPN 3 HD"),
                "exact ESPN should outrank numbered variants");
        require(SportsChannelResolver.nameScore("Sky Sports NFL", "Sky Sports NFL HD") >= 90,
                "Sky Sports NFL should tolerate quality suffixes");
        require(SportsChannelResolver.nameScore("Sky Sports NFL", "Sky Cinema") < 75,
                "unrelated Sky channels must not pass the resolver threshold");
        require(SportsChannelResolver.nameScore("Sky Sports NFL", "Sky Sports Cricket") < 75,
                "brand overlap must not confuse different sports channels");

        require(SportsRegion.countryScore("Peru", "Peru")
                        > SportsRegion.countryScore("United Kingdom", "Peru"),
                "local broadcaster should outrank foreign broadcaster");
        require(SportsRegion.countryScore("Mexico", "Peru")
                        > SportsRegion.countryScore("United Kingdom", "Peru"),
                "LatAm broadcaster should outrank unrelated region");

        List<SportsBroadcaster> ranked = SportsChannelResolver.prioritizedBroadcasters(List.of(
                new SportsBroadcaster("Sky Sports NFL", "United Kingdom", null),
                new SportsBroadcaster("ESPN", "Peru", null),
                new SportsBroadcaster("ESPN Mexico", "Mexico", null)), "Peru");
        require(ranked.size() == 2, "regional broadcasters should suppress unrelated-region entries");
        require("Peru".equals(ranked.get(0).country()), "exact country should rank first");

        require(!TheSportsDbProvider.looksLive("NS", null), "NS is not live");
        require(!TheSportsDbProvider.looksLive("FT", null), "FT is not live");
        require(TheSportsDbProvider.looksLive("2H", "67'"), "second half should be live");
        require(TheSportsDbProvider.looksLive("Live", null), "explicit Live should be live");

        System.out.println("SportsResolverTest OK");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
