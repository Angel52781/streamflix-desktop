package dev.streamflix.desktop;

import java.util.List;

public final class UserDataTest {
    public static void main(String[] args) {
        setup();
        testProviderNamespacing();
        
        setup();
        testSerialization();
        
        setup();
        testProgressRules();

        System.out.println("UserDataTest OK");
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

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError("Failed: " + name);
    }
}
