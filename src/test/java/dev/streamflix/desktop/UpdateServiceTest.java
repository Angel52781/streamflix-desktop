package dev.streamflix.desktop;

import java.util.Optional;

public final class UpdateServiceTest {
    public static void main(String[] args) {
        comparesVersions();
        parsesStableRelease();
        ignoresCurrentAndPrerelease();
        requiresChecksum();
        System.out.println("UpdateServiceTest OK");
    }

    private static void comparesVersions() {
        check(UpdateService.compareVersions("1.3.5", "1.3.4") > 0, "newer patch");
        check(UpdateService.compareVersions("1.4.0", "1.3.99") > 0, "newer minor");
        check(UpdateService.compareVersions("1.3.4", "1.3.4") == 0, "same");
        check(UpdateService.compareVersions("v2.0", "1.99.99") > 0, "v prefix");
    }

    private static void parsesStableRelease() {
        String json = releaseJson("v1.3.5", false, true);
        Optional<UpdateService.ReleaseInfo> update =
                UpdateService.findUpdate(json, "1.3.4");
        check(update.isPresent(), "update present");
        check("1.3.5".equals(update.get().version()), "version");
        check(UpdateService.STABLE_ZIP.equals(update.get().zip().name()), "stable zip");
        check(UpdateService.STABLE_SHA.equals(update.get().checksum().name()), "stable checksum");
    }
    private static void ignoresCurrentAndPrerelease() {
        check(UpdateService.findUpdate(releaseJson("v1.3.4", false, true), "1.3.4").isEmpty(),
                "current release ignored");
        check(UpdateService.findUpdate(releaseJson("v1.4.0", true, true), "1.3.4").isEmpty(),
                "prerelease ignored");
    }

    private static void requiresChecksum() {
        check(UpdateService.findUpdate(releaseJson("v1.3.5", false, false), "1.3.4").isEmpty(),
                "checksum required");
        String sha = "E99CE043BD67C6C6CCC3649415AA5111BB6DD388334F041212B0390CFB2427AD";
        check(sha.equals(PortableUpdater.expectedSha(sha + " *StreamflixDesktop-windows.zip")),
                "checksum parser");
    }

    private static String releaseJson(String tag, boolean prerelease, boolean checksum) {
        String shaAsset = checksum
                ? ",{\"name\":\"" + UpdateService.STABLE_SHA
                + "\",\"browser_download_url\":\"https://example.test/app.sha256\",\"size\":95}"
                : "";
        return "{"
                + "\"tag_name\":\"" + tag + "\","
                + "\"name\":\"Test release\","
                + "\"body\":\"Changes\","
                + "\"html_url\":\"https://example.test/release\","
                + "\"draft\":false,"
                + "\"prerelease\":" + prerelease + ","
                + "\"assets\":[{\"name\":\"" + UpdateService.STABLE_ZIP
                + "\",\"browser_download_url\":\"https://example.test/app.zip\","
                + "\"size\":100}" + shaAsset + "]"
                + "}";
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
