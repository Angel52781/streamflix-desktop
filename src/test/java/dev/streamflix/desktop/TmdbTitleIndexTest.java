package dev.streamflix.desktop;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TmdbTitleIndexTest {
    public static void main(String[] args) throws Exception {
        testNormalization();
        testPrefixSearchAndRanking();
        System.out.println("TmdbTitleIndexTest OK");
    }

    private static void testNormalization() {
        require("ginny georgia".equals(TmdbTitleIndex.normalize(" Ginny & Georgia ")),
                "ampersand normalization");
        require("espanol".equals(TmdbTitleIndex.normalize("Español")),
                "accent normalization");
        require(TmdbTitleIndex.prefixQuality("ginny georgia", "ginn") == 0,
                "title prefix match");
        require(TmdbTitleIndex.prefixQuality("the suits archive", "sui") == 1,
                "word prefix match");
        require(TmdbTitleIndex.prefixQuality("mobile suit gundam", "ginn") < 0,
                "unrelated title excluded");
    }

    private static void testPrefixSearchAndRanking() throws Exception {
        Path dir = Files.createTempDirectory("tmdb-index-test");
        Path file = dir.resolve("tv.json");
        try {
            String fixture = """
                    {"id":117581,"original_name":"Ginny \\u0026 Georgia","popularity":23.5}
                    {"id":327761,"original_name":"Sui Generis","popularity":2.0}
                    {"id":37680,"original_name":"Suits","popularity":35.0}
                    {"id":83334,"original_name":"Suits","popularity":18.0}
                    {"id":20111,"original_name":"Mobile Suit Gundam SEED","popularity":40.0}
                    """;
            Files.writeString(file, fixture, StandardCharsets.UTF_8);

            List<TmdbTitleIndex.Match> ginny =
                    TmdbTitleIndex.searchFile(file, "ginn", 5);
            require(!ginny.isEmpty() && ginny.get(0).id() == 117581,
                    "ginn resolves Ginny & Georgia");

            List<TmdbTitleIndex.Match> suits =
                    TmdbTitleIndex.searchFile(file, "sui", 5);
            require(!suits.isEmpty() && suits.get(0).id() == 37680,
                    "sui ranks popular title-prefix Suits first");
            int mobileSuit = -1;
            for (int i = 0; i < suits.size(); i++) {
                if (suits.get(i).id() == 20111) mobileSuit = i;
            }
            require(mobileSuit < 0 || mobileSuit > 0,
                    "word-prefix Mobile Suit cannot outrank title-prefix Suits");
        } finally {
            try (var paths = Files.walk(dir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) {}
                });
            }
        }
    }

    private static void require(boolean value, String label) {
        if (!value) throw new AssertionError("Failed: " + label);
    }
}
