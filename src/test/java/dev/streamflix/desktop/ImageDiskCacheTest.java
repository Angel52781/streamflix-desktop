package dev.streamflix.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class ImageDiskCacheTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("streamflix-image-cache-test-");
        System.setProperty("streamflix.image.cache.dir", dir.toString());
        try {
            byte[] first = new byte[] {1, 2, 3, 4, 5};
            String url = "https://example.test/poster.webp";

            check(ImageDiskCache.read(url) == null, "cache initially empty");
            ImageDiskCache.write(url, first);
            check(Arrays.equals(first, ImageDiskCache.read(url)), "cache round trip");

            byte[] second = new byte[] {9, 8, 7};
            ImageDiskCache.write("https://example.test/other.webp", second);
            check(Arrays.equals(second,
                    ImageDiskCache.read("https://example.test/other.webp")), "separate key");

            ImageDiskCache.invalidate(url);
            check(ImageDiskCache.read(url) == null, "invalidate");
            System.out.println("ImageDiskCacheTest OK");
        } finally {
            System.clearProperty("streamflix.image.cache.dir");
            deleteRecursively(dir);
        }
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
