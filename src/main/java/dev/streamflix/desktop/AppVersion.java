package dev.streamflix.desktop;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Version source that works both from the packaged JAR manifest and the development checkout. */
final class AppVersion {
    private AppVersion() {}

    static String current() {
        Package pkg = App.class.getPackage();
        if (pkg != null) {
            String implementation = pkg.getImplementationVersion();
            if (implementation != null && !implementation.isBlank()) return implementation.strip();
        }

        try {
            Path local = Path.of("VERSION").toAbsolutePath().normalize();
            if (Files.isRegularFile(local)) {
                String value = Files.readString(local, StandardCharsets.UTF_8).strip();
                if (!value.isBlank()) return value;
            }
        } catch (Exception ignored) {}

        return "desarrollo";
    }
}
