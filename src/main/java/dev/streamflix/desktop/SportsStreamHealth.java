package dev.streamflix.desktop;

import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

final class SportsStreamHealth {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "sports-stream-health");
        t.setDaemon(true);
        return t;
    });

    List<SportsChannelHealth> rank(List<SportsChannelCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<SportsChannelCandidate> sample = candidates.stream().limit(10).toList();
        List<CompletableFuture<SportsChannelHealth>> futures = sample.stream()
                .map(candidate -> CompletableFuture.supplyAsync(() -> probe(candidate), pool))
                .toList();
        ArrayList<SportsChannelHealth> out = new ArrayList<>();
        for (CompletableFuture<SportsChannelHealth> future : futures) {
            try { out.add(future.get(7, TimeUnit.SECONDS)); }
            catch (Exception ignored) {}
        }
        out.sort(Comparator
                .comparing(SportsChannelHealth::healthy).reversed()
                .thenComparingInt(value -> -value.candidate().score())
                .thenComparingLong(SportsChannelHealth::latencyMillis));
        return List.copyOf(out);
    }

    private SportsChannelHealth probe(SportsChannelCandidate candidate) {
        long started = System.nanoTime();
        try {
            M3uLiveProvider.ChannelSource source = M3uLiveProvider.decode(candidate.channel().providerId());
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(source.url()))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .header("User-Agent", source.userAgent() == null ? Http.USER_AGENT : source.userAgent())
                    .header("Accept", "application/vnd.apple.mpegurl,application/x-mpegURL,video/*,*/*");
            if (source.referrer() != null) builder.header("Referer", source.referrer());
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream ignored = response.body()) {
                boolean healthy = response.statusCode() >= 200 && response.statusCode() < 400;
                return new SportsChannelHealth(candidate, healthy, elapsed(started));
            }
        } catch (Exception ex) {
            return new SportsChannelHealth(candidate, false, elapsed(started));
        }
    }

    private static long elapsed(long started) {
        return Math.max(1L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }
}
