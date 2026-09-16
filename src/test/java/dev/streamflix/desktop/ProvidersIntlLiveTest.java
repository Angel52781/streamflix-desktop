package dev.streamflix.desktop;

public final class ProvidersIntlLiveTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== RUNNING INTERNATIONAL PROVIDERS LIVE TEST SUITE ===");

        System.out.println("\n--- Testing AnimeSaturn ---");
        AnimeSaturnLiveTest.main(args);

        System.out.println("\n--- Testing AnimeUnity ---");
        AnimeUnityLiveTest.main(args);

        System.out.println("\n--- Testing MEGAKino ---");
        MegaKinoLiveTest.main(args);

        System.out.println("\n=== ALL INTERNATIONAL PROVIDERS PASSED LIVE SMOKE VALIDATION ===");
    }
}
