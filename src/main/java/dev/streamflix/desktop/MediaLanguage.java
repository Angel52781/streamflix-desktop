package dev.streamflix.desktop;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Canonical language matching for source labels and mpv track metadata. */
final class MediaLanguage {
    private static final Set<String> SPANISH = Set.of(
            "es", "spa", "esp", "spanish", "espanol", "castellano", "castilian",
            "latino", "latina", "latam", "latin");
    private static final Set<String> ENGLISH = Set.of(
            "en", "eng", "english", "ingles");

    private MediaLanguage() {}

    static boolean matches(String language, String title, String preferred) {
        if (!"es".equals(preferred) && !"en".equals(preferred)) return false;
        Set<String> words = words(language, title);
        Set<String> aliases = "es".equals(preferred) ? SPANISH : ENGLISH;
        return !Collections.disjoint(words, aliases);
    }

    static int rank(String language, String title, String preferred) {
        if (matches(language, title, preferred)) return 0;
        String fallback = "en".equals(preferred) ? "es" : "en";
        if (matches(language, title, fallback)) return 1;
        return 2;
    }

    private static Set<String> words(String... values) {
        HashSet<String> out = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "")
                    .toLowerCase(Locale.ROOT);
            out.addAll(Arrays.asList(normalized.split("[^a-z0-9]+")));
            // Some sources use compact BCP-47-ish labels.
            out.add(normalized.replaceAll("[^a-z0-9]+", ""));
        }
        out.remove("");
        return out;
    }
}
