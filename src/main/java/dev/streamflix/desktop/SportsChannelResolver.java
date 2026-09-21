package dev.streamflix.desktop;

import java.text.Normalizer;
import java.util.*;

final class SportsChannelResolver {
    private static final Set<String> GENERIC = Set.of(
            "hd", "fhd", "uhd", "4k", "tv", "channel", "canal", "network", "live");

    private final List<Provider> liveProviders;
    private final String preferredCountry;

    SportsChannelResolver(List<Provider> providers) {
        this(providers, SportsRegion.preferredCountry());
    }

    SportsChannelResolver(List<Provider> providers, String preferredCountry) {
        this.liveProviders = providers.stream().filter(M3uLiveProvider.class::isInstance).toList();
        this.preferredCountry = preferredCountry == null ? "" : preferredCountry.trim();
    }

    List<SportsChannelCandidate> resolve(SportsEvent event) {
        if (event == null || event.broadcasters().isEmpty()) return List.of();
        List<SportsBroadcaster> broadcasters = prioritizedBroadcasters(event.broadcasters(), preferredCountry);

        LinkedHashMap<String, SportsChannelCandidate> bestByChannel = new LinkedHashMap<>();
        for (SportsBroadcaster broadcaster : broadcasters) {
            for (Provider provider : liveProviders) {
                collect(provider, broadcaster, bestByChannel);
            }
        }

        return bestByChannel.values().stream()
                .sorted(Comparator.comparingInt(SportsChannelCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.channel().title()))
                .limit(16)
                .toList();
    }

    static List<SportsBroadcaster> prioritizedBroadcasters(
            List<SportsBroadcaster> input, String preferredCountry) {
        ArrayList<SportsBroadcaster> broadcasters = new ArrayList<>(input == null ? List.of() : input);
        String preferred = preferredCountry == null ? "" : preferredCountry.trim();
        broadcasters.sort(Comparator.comparingInt(
                (SportsBroadcaster b) -> SportsRegion.countryScore(b.country(), preferred)).reversed());
        if (!preferred.isBlank()) {
            List<SportsBroadcaster> regional = broadcasters.stream()
                    .filter(b -> SportsRegion.countryScore(b.country(), preferred) >= 18)
                    .toList();
            if (!regional.isEmpty()) return regional;
        }
        return List.copyOf(broadcasters);
    }

    private void collect(Provider provider, SportsBroadcaster broadcaster,
            Map<String, SportsChannelCandidate> bestByChannel) {
        for (String query : queries(broadcaster.channel())) {
            try {
                for (Models.ShowItem channel : provider.search(query, 1)) {
                    int name = nameScore(broadcaster.channel(), channel.title());
                    if (name < 75) continue;
                    int score = name
                            + SportsRegion.countryScore(broadcaster.country(), preferredCountry)
                            + providerRegionBonus(provider.id(), broadcaster.country())
                            + qualityBonus(channel.title());
                    SportsChannelCandidate candidate = new SportsChannelCandidate(
                            broadcaster, provider.id(), provider.name(), channel, score);
                    String key = provider.id() + "|" + channel.providerId();
                    SportsChannelCandidate previous = bestByChannel.get(key);
                    if (previous == null || score > previous.score()) bestByChannel.put(key, candidate);
                }
            } catch (Exception ex) {
                AppLog.warn("sports", "No se pudo buscar " + broadcaster.channel()
                        + " en " + provider.name(), ex);
            }
        }
    }

    static int nameScore(String broadcaster, String channel) {
        String a = normalize(broadcaster);
        String b = normalize(channel);
        if (a.isBlank() || b.isBlank()) return 0;
        if (a.equals(b)) return 100;
        if (b.contains(a) || a.contains(b)) return 85;

        Set<String> at = tokens(a);
        Set<String> bt = tokens(b);
        if (at.isEmpty() || bt.isEmpty()) return 0;
        long shared = at.stream().filter(bt::contains).count();
        double coverage = shared / (double) at.size();
        HashSet<String> union = new HashSet<>(at);
        union.addAll(bt);
        double jaccard = shared / (double) union.size();
        int score = (int) Math.round(coverage * 65 + jaccard * 25);
        String first = at.iterator().next();
        if (bt.contains(first)) score += 10;
        return Math.min(100, score);
    }

    private static List<String> queries(String broadcaster) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (broadcaster == null || broadcaster.isBlank()) return List.of();
        out.add(broadcaster.trim());
        List<String> parts = new ArrayList<>(tokens(normalize(broadcaster)));
        if (parts.size() >= 2) out.add(String.join(" ", parts.subList(0, 2)));
        if (!parts.isEmpty()) out.add(parts.get(0));
        return List.copyOf(out);
    }

    private static int providerRegionBonus(String providerId, String broadcasterCountry) {
        String country = broadcasterCountry == null ? "" : broadcasterCountry.toLowerCase(Locale.ROOT);
        if (providerId.equals("iptv-spain") && country.contains("spain")) return 12;
        if (providerId.equals("pluto-mx") && country.contains("mexico")) return 12;
        if (providerId.equals("pluto-es") && country.contains("spain")) return 12;
        if (providerId.equals("pluto-us") && country.contains("united states")) return 12;
        if (providerId.equals("iptv-all-world")) return 4;
        return 0;
    }

    private static int qualityBonus(String title) {
        if (title == null) return 0;
        String value = title.toLowerCase(Locale.ROOT);
        if (value.contains("2160p") || value.contains("4k") || value.contains("uhd")) return 10;
        if (value.contains("1080p") || value.contains("fhd")) return 8;
        if (value.contains("720p") || value.matches(".*\\bhd\\b.*")) return 5;
        return 0;
    }

    private static Set<String> tokens(String value) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String token : value.split("\\s+")) {
            if (token.length() < 2 || GENERIC.contains(token)) continue;
            out.add(token);
        }
        return out;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return ascii.toLowerCase(Locale.ROOT)
                .replaceAll("\\b(?:2160|1440|1080|720|576|480)p\\b", " ")
                .replaceAll("\\b(?:hd|fhd|uhd|4k)\\b", " ")
                .replace('&', ' ')
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
