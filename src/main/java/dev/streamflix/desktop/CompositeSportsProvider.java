package dev.streamflix.desktop;

import java.text.Normalizer;
import java.time.Duration;
import java.util.*;

final class CompositeSportsProvider implements SportsDataProvider {
    private static final long CACHE_MILLIS = Duration.ofSeconds(55).toMillis();
    private final List<SportsDataProvider> providers;
    private volatile SportsSnapshot cached;
    private volatile long cachedAt;

    CompositeSportsProvider(SportsDataProvider... providers) {
        this.providers = List.of(providers);
    }

    @Override public synchronized SportsSnapshot load() throws Exception {
        long now = System.currentTimeMillis();
        SportsSnapshot current = cached;
        if (current != null && now - cachedAt < CACHE_MILLIS) return current;

        ArrayList<SportsSnapshot> snapshots = new ArrayList<>();
        Exception last = null;
        for (SportsDataProvider provider : providers) {
            try { snapshots.add(provider.load()); }
            catch (Exception ex) {
                last = ex;
                AppLog.warn("sports", "Fuente deportiva degradada: " + provider.getClass().getSimpleName(), ex);
            }
        }
        if (snapshots.isEmpty()) throw last != null ? last : new IllegalStateException("Sin fuentes deportivas disponibles");

        SportsSnapshot merged = merge(snapshots);
        cached = merged;
        cachedAt = now;
        return merged;
    }

    static SportsSnapshot merge(List<SportsSnapshot> snapshots) {
        LinkedHashMap<String, SportsEvent> live = new LinkedHashMap<>();
        LinkedHashMap<String, SportsEvent> today = new LinkedHashMap<>();
        LinkedHashMap<String, SportsEvent> upcoming = new LinkedHashMap<>();
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        boolean limited = false;

        for (SportsSnapshot snapshot : snapshots) {
            sources.add(snapshot.sourceName());
            limited |= snapshot.limitedCoverage();
            mergeInto(live, snapshot.live());
            mergeInto(today, snapshot.today());
            mergeInto(upcoming, snapshot.upcoming());
        }
        // Never duplicate a live event in the normal Today shelf.
        Set<String> liveKeys = new HashSet<>();
        for (SportsEvent event : live.values()) liveKeys.add(dedupeKey(event));
        today.entrySet().removeIf(entry -> liveKeys.contains(dedupeKey(entry.getValue())));

        Comparator<SportsEvent> byStart = Comparator.comparing(
                SportsEvent::startsAt, Comparator.nullsLast(Comparator.naturalOrder()));
        return new SportsSnapshot(
                live.values().stream().sorted(byStart).toList(),
                today.values().stream().sorted(byStart).toList(),
                upcoming.values().stream().sorted(byStart).toList(),
                limited && snapshots.size() == 1,
                String.join(" · ", sources));
    }

    private static void mergeInto(Map<String, SportsEvent> target, List<SportsEvent> events) {
        for (SportsEvent event : events) {
            String key = dedupeKey(event);
            target.merge(key, event, CompositeSportsProvider::richer);
        }
    }

    private static SportsEvent richer(SportsEvent a, SportsEvent b) {
        List<SportsBroadcaster> broadcasters = mergeBroadcasters(a.broadcasters(), b.broadcasters());
        return new SportsEvent(
                a.id(),
                prefer(a.sport(), b.sport()),
                prefer(a.league(), b.league()),
                prefer(a.name(), b.name()),
                prefer(a.homeTeam(), b.homeTeam()), prefer(a.awayTeam(), b.awayTeam()),
                prefer(a.homeBadge(), b.homeBadge()), prefer(a.awayBadge(), b.awayBadge()),
                a.homeScore() != null ? a.homeScore() : b.homeScore(),
                a.awayScore() != null ? a.awayScore() : b.awayScore(),
                preferLive(a.status(), b.status()),
                prefer(a.progress(), b.progress()),
                a.startsAt() != null ? a.startsAt() : b.startsAt(),
                broadcasters);
    }

    private static List<SportsBroadcaster> mergeBroadcasters(List<SportsBroadcaster> a, List<SportsBroadcaster> b) {
        LinkedHashMap<String, SportsBroadcaster> out = new LinkedHashMap<>();
        for (SportsBroadcaster value : a) out.put(key(value), value);
        for (SportsBroadcaster value : b) out.putIfAbsent(key(value), value);
        return List.copyOf(out.values());
    }

    private static String key(SportsBroadcaster b) {
        return normalize(b.channel()) + "|" + normalize(b.country());
    }

    static String dedupeKey(SportsEvent event) {
        String participants = event.hasTeams()
                ? normalize(event.homeTeam()) + "|" + normalize(event.awayTeam())
                : normalize(event.name());
        long bucket = event.startsAt() == null ? 0L : event.startsAt().getEpochSecond() / 7200L;
        return normalize(event.sport()) + "|" + participants + "|" + bucket;
    }

    private static String prefer(String a, String b) { return a != null && !a.isBlank() ? a : b; }
    private static String preferLive(String a, String b) {
        if (TheSportsDbProvider.looksLive(a, null)) return a;
        if (TheSportsDbProvider.looksLive(b, null)) return b;
        return prefer(a, b);
    }
    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
