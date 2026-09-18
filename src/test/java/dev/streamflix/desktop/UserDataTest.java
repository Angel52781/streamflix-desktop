package dev.streamflix.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class UserDataTest {
    public static void main(String[] args) throws Exception {
        Path testDir = Files.createTempDirectory("streamflix-userdata-test-");
        System.setProperty("streamflix.data.dir", testDir.toString());
        try {
            setup();
            testProviderNamespacing();

            setup();
            testSerialization();

            setup();
            testProgressRules();

            setup();
            testEpisodeProgressMetadata();

            setup();
            testCorruptFilesDoNotPreventLoad(testDir);

            System.out.println("UserDataTest OK");
        } finally {
            UserData.clearForTests();
            Files.deleteIfExists(testDir);
            System.clearProperty("streamflix.data.dir");
        }
    }

    private static void setup() {
        UserData.clearForTests();
    }

    private static void testProviderNamespacing() {
        Models.ShowItem item1 = new Models.ShowItem("same-id", "remote-1", "Title A", null, null, null, null, null, null, Models.ShowType.MOVIE);
        Models.ShowItem item2 = new Models.ShowItem("same-id", "remote-1", "Title B", null, null, null, null, null, null, Models.ShowType.MOVIE);

        UserData.toggleFavorite("source-a", item1);
        UserData.toggleFavorite("source-b", item2);

        require(UserData.isFavorite("source-a", "same-id"), "source-a should be favorite");
        require(UserData.isFavorite("source-b", "same-id"), "source-b should be favorite");
        require(!UserData.isFavorite("source-a", "other-id"), "source-a/other-id should not be favorite");

        UserData.toggleFavorite("source-a", item1);
        require(!UserData.isFavorite("source-a", "same-id"), "source-a should be toggled off");
        require(UserData.isFavorite("source-b", "same-id"), "source-b should still be favorite");
    }

    private static void testSerialization() {
        Models.ShowItem item = new Models.ShowItem("serial1", "prov1", "Some Title", "Overview here", "2024", 120, 8.5, "poster.jpg", "banner.jpg", Models.ShowType.TV_SHOW);
        UserData.toggleFavorite("source-serialization", item);

        UserData.loadForTests();
        List<Models.ShowItem> favs = UserData.getFavorites();
        require(favs.size() == 1, "size == 1");
        Models.ShowItem loaded = favs.get(0);
        require("serial1".equals(loaded.id()), "id match");
        require("Some Title".equals(loaded.title()), "title match");
        require(Models.ShowType.TV_SHOW == loaded.type(), "type match");
        require(Integer.valueOf(120).equals(loaded.runtimeMinutes()), "runtime match");
        require("source-serialization".equals(loaded.sourceProviderId()), "source provider match");
    }

    private static void testProgressRules() {
        Models.ShowItem item = new Models.ShowItem("prog1", "prov1", "Prog Title", null, null, null, null, null, null, Models.ShowType.MOVIE);

        UserData.recordHistory("source-progress", item);
        require(UserData.getHistory().size() == 1, "history size == 1");

        UserData.recordHistory("source-progress", item, 45.0, 300.0);
        require(UserData.getHistory().size() == 1, "history size still 1");

        UserData.recordHistory("source-progress", item);

        double progress = UserData.getProgressForTest("source-progress", item);
        require(progress == 45.0, "progress should not be overwritten by 0.0 updates. Was: " + progress);
    }

    private static void testEpisodeProgressMetadata() {
        Models.ShowItem show = new Models.ShowItem(
                "show1", "tv/5920", "The Mentalist", null, null, null, null,
                null, null, Models.ShowType.TV_SHOW);
        Models.Episode episode = new Models.Episode(
                "tv/5920/season/2/episode/1", 2, 1, "Redemption", null, null);

        UserData.recordEpisodeHistory("tmdb-en", show, episode, 123.0, 2400.0);

        UserData.HistoryEntry entry = UserData.getHistoryEntry("tmdb-en", show);
        require(entry != null, "episode history entry exists");
        require("tv/5920/season/2/episode/1".equals(entry.mediaId()), "episode media id persisted");
        require(Integer.valueOf(2).equals(entry.seasonNumber()), "season persisted");
        require(Integer.valueOf(1).equals(entry.episodeNumber()), "episode persisted");
        require(entry.progressSeconds() == 123.0, "episode progress persisted");
        require(Math.abs(UserData.progressFraction("tmdb-en", show) - (123.0 / 2400.0)) < 0.0001,
                "progress fraction computed");

        UserData.loadForTests();
        UserData.HistoryEntry reloaded = UserData.getHistoryEntry("tmdb-en", show);
        require(reloaded != null, "episode history survives reload");
        require(Integer.valueOf(2).equals(reloaded.seasonNumber()), "season survives reload");
        require(Integer.valueOf(1).equals(reloaded.episodeNumber()), "episode survives reload");
    }

    private static void testCorruptFilesDoNotPreventLoad(Path testDir) throws Exception {
        Files.writeString(testDir.resolve("favorites.json"), "{not-json");
        Files.writeString(testDir.resolve("history.json"), "[broken");
        UserData.loadForTests();
        require(UserData.getFavorites().isEmpty(), "corrupt favorites should load as empty");
        require(UserData.getHistory().isEmpty(), "corrupt history should load as empty");
    }

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError("Failed: " + name);
    }
}
