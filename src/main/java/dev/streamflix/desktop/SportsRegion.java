package dev.streamflix.desktop;

import java.util.Locale;
import java.util.Set;

final class SportsRegion {
    static final String ENV_COUNTRY = "STREAMFLIX_SPORTS_COUNTRY";
    private static final Set<String> LATAM = Set.of(
            "Argentina", "Bolivia", "Brazil", "Chile", "Colombia", "Costa Rica",
            "Cuba", "Dominican Republic", "Ecuador", "El Salvador", "Guatemala",
            "Honduras", "Mexico", "Nicaragua", "Panama", "Paraguay", "Peru",
            "Puerto Rico", "Uruguay", "Venezuela");

    private SportsRegion() {}

    static String preferredCountry() {
        String explicit = System.getenv(ENV_COUNTRY);
        if (explicit != null && !explicit.isBlank()) return explicit.trim();
        String code = Locale.getDefault().getCountry();
        if (code == null || code.isBlank()) return "";
        return new Locale("", code).getDisplayCountry(Locale.ENGLISH);
    }

    static int countryScore(String broadcasterCountry, String preferredCountry) {
        String source = normalize(broadcasterCountry);
        String preferred = normalize(preferredCountry);
        if (source.isBlank()) return 5;
        if (!preferred.isBlank() && source.equals(preferred)) return 40;
        if (source.contains("international") || source.contains("worldwide") || source.contains("global")) return 22;
        if (!preferred.isBlank() && isLatam(source) && isLatam(preferred)) return 18;
        if (source.equals("united states") || source.equals("usa")) return 8;
        return 0;
    }

    static boolean isLatam(String country) {
        String normalized = normalize(country);
        return LATAM.stream().map(SportsRegion::normalize).anyMatch(normalized::equals);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
