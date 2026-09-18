package dev.streamflix.desktop;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipInputStream;

public final class DiagnosticsTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("streamflix-diagnostics-test-");
        Path data = root.resolve("data");
        Path logs = root.resolve("logs");
        System.setProperty("streamflix.data.dir", data.toString());
        System.setProperty("streamflix.log.dir", logs.toString());

        String fakeKey = "0123456789abcdef0123456789abcdef";
        try {
            TmdbSettings.saveApiKey(fakeKey);
            AppLog.warn("test", "api_key=" + fakeKey + " Bearer " + fakeKey);

            Path output = root.resolve("diagnostics.zip");
            Diagnostics.export(output);
            check(Files.isRegularFile(output), "zip created");

            String exported = readZipText(output);
            check(exported.contains("tmdbConfigured=true"), "configured flag");
            check(exported.contains("api_key=<redacted>"), "query credential redacted");
            check(exported.contains("Bearer <redacted>"), "bearer credential redacted");
            check(!exported.contains(fakeKey), "credential not exported");
            System.out.println("DiagnosticsTest OK");
        } finally {
            System.clearProperty("streamflix.data.dir");
            System.clearProperty("streamflix.log.dir");
            try (var walk = Files.walk(root)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static String readZipText(Path zip) throws Exception {
        StringBuilder out = new StringBuilder();
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
            java.util.zip.ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                input.transferTo(bytes);
                out.append(entry.getName()).append('\n')
                        .append(bytes.toString(StandardCharsets.UTF_8)).append('\n');
                input.closeEntry();
            }
        }
        return out.toString();
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
