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
        Models.ShowItem item1 = new Models.ShowItem("1", "p1", "Title", null, null, null, null, null, null, Models.ShowType.MOVIE);
        Models.ShowItem item2 = new Models.ShowItem("1", "p2", "Title", null, null, null, null, null, null, Models.ShowType.MOVIE);
        
        UserData.toggleFavorite(item1);
        UserData.toggleFavorite(item2);
        
        require(UserData.isFavorite("p1", "1"), "p1 should be favorite");
        require(UserData.isFavorite("p2", "1"), "p2 should be favorite");
        require(!UserData.isFavorite("p1", "2"), "p1/2 should not be favorite");
        
        UserData.toggleFavorite(item1);
        require(!UserData.isFavorite("p1", "1"), "p1 should be toggled off");
        require(UserData.isFavorite("p2", "1"), "p2 should still be favorite");
    }

    private static void testSerialization() {
        Models.ShowItem item = new Models.ShowItem("serial1", "prov1", "Some Title", "Overview here", "2024", 120, 8.5, "poster.jpg", "banner.jpg", Models.ShowType.TV_SHOW);
        UserData.toggleFavorite(item);
        
        UserData.loadForTests();
        List<Models.ShowItem> favs = UserData.getFavorites();
        require(favs.size() == 1, "size == 1");
        Models.ShowItem loaded = favs.get(0);
        require("serial1".equals(loaded.id()), "id match");
        require("Some Title".equals(loaded.title()), "title match");
        require(Models.ShowType.TV_SHOW == loaded.type(), "type match");
        require(Integer.valueOf(120).equals(loaded.runtimeMinutes()), "runtime match");
    }

    private static void testProgressRules() {
        Models.ShowItem item = new Models.ShowItem("prog1", "prov1", "Prog Title", null, null, null, null, null, null, Models.ShowType.MOVIE);

        UserData.recordHistory(item);
        require(UserData.getHistory().size() == 1, "history size == 1");

        UserData.recordHistory(item, 45.0, 300.0);
        require(UserData.getHistory().size() == 1, "history size still 1");

        UserData.recordHistory(item);

        double progress = UserData.getProgressForTest(item);
        require(progress == 45.0, "progress should not be overwritten by 0.0 updates. Was: " + progress);
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
