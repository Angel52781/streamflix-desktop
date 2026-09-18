package dev.streamflix.desktop;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

final class ImageDiskCache {
    private static final String CACHE_DIR_PROPERTY = "streamflix.image.cache.dir";
    private static final long MAX_CACHE_BYTES = 256L * 1024L * 1024L;
    private static final long TARGET_CACHE_BYTES = 220L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 20L * 1024L * 1024L;
    private static final long MAX_AGE_MILLIS = Duration.ofDays(30).toMillis();
    private static final long CLEANUP_INTERVAL_MILLIS = Duration.ofHours(6).toMillis();
    private static final Object CLEANUP_LOCK = new Object();
    private static volatile long lastCleanupAt;

    private ImageDiskCache() {}

    static byte[] read(String url) {
        if (url == null || url.isBlank()) return null;
        Path file = fileFor(url);
        try {
            if (!Files.isRegularFile(file)) return null;
            long size = Files.size(file);
            long age = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis();
            if (size <= 0L || size > MAX_ENTRY_BYTES || age > MAX_AGE_MILLIS) {
                Files.deleteIfExists(file);
                return null;
            }

            byte[] bytes = Files.readAllBytes(file);
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
            return bytes;
        } catch (IOException ignored) {
            return null;
        }
    }

    static void write(String url, byte[] bytes) {
        if (url == null || url.isBlank() || bytes == null
                || bytes.length == 0 || bytes.length > MAX_ENTRY_BYTES) {
            return;
        }

        Path target = fileFor(url);
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), "image-", ".part");
            Files.write(temp, bytes);
            try {
                Files.move(temp, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.setLastModifiedTime(target, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException ignored) {
            // Cache failures must never break catalog rendering.
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            }
        }
        cleanupIfNeeded();
    }

    static void invalidate(String url) {
        if (url == null || url.isBlank()) return;
        try { Files.deleteIfExists(fileFor(url)); }
        catch (IOException ignored) {}
    }

    private static void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupAt < CLEANUP_INTERVAL_MILLIS) return;

        synchronized (CLEANUP_LOCK) {
            now = System.currentTimeMillis();
            if (now - lastCleanupAt < CLEANUP_INTERVAL_MILLIS) return;
            lastCleanupAt = now;
            cleanup(now);
        }
    }

    private static void cleanup(long now) {
        Path dir = cacheDir();
        if (!Files.isDirectory(dir)) return;
        try {
            List<Entry> entries = new ArrayList<>();
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    try {
                        long modified = Files.getLastModifiedTime(path).toMillis();
                        long size = Files.size(path);
                        if (now - modified > MAX_AGE_MILLIS || size <= 0L) {
                            Files.deleteIfExists(path);
                        } else {
                            entries.add(new Entry(path, size, modified));
                        }
                    } catch (IOException ignored) {}
                });
            }

            long total = entries.stream().mapToLong(Entry::size).sum();
            if (total <= MAX_CACHE_BYTES) return;

            entries.sort(Comparator.comparingLong(Entry::modifiedAt));
            for (Entry entry : entries) {
                if (total <= TARGET_CACHE_BYTES) break;
                try {
                    if (Files.deleteIfExists(entry.path())) total -= entry.size();
                } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {
            // Best-effort maintenance only.
        }
    }

    private static Path fileFor(String url) {
        return cacheDir().resolve(hash(url) + ".img");
    }
    private static Path cacheDir() {
        String override = System.getProperty(CACHE_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Local")
                : Path.of(localAppData);
        return base.resolve("Streamflix").resolve("cache").resolve("images")
                .toAbsolutePath().normalize();
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private record Entry(Path path, long size, long modifiedAt) {}
}
