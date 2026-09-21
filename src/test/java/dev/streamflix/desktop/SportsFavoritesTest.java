package dev.streamflix.desktop;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

public final class SportsFavoritesTest {
    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("streamflix-sports-favorites-test");
        System.setProperty("streamflix.data.dir", dir.toString());
        SportsEvent event = new SportsEvent("fav", "Fútbol", "Liga 1", "Alianza vs Universitario",
                "Alianza Lima", "Universitario", null, null, null, null, "NS", null,
                Instant.now(), List.of());
        require(!SportsFavorites.matches(event), "fixture starts unfollowed");
        SportsFavorites.toggle("team", "Alianza Lima", "Fútbol");
        require(SportsFavorites.matches(event), "followed team should match event");
        require(Files.exists(dir.resolve("sports-favorites.json")), "sports favorites should persist separately");
        SportsFavorites.toggle("team", "Alianza Lima", "Fútbol");
        require(!SportsFavorites.matches(event), "toggle should unfollow team");
        System.out.println("SportsFavoritesTest OK");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
