package dev.streamflix.desktop;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class M3uLiveProvider implements Provider {
    private static final int PAGE_SIZE = 50;
    private static final long CACHE_MILLIS = 30L * 60L * 1000L;

    private final String providerId;
    private final String providerName;
    private final String playlistUrl;
    private final Http http = new Http();
    private volatile List<M3uPlaylist.Channel> cached = List.of();
    private volatile long cachedAt;

    M3uLiveProvider(String providerId, String providerName, String playlistUrl) {
        this.providerId = providerId;
        this.providerName = providerName;
        this.playlistUrl = playlistUrl;
    }

    @Override public String id() { return providerId; }
    @Override public String name() { return providerName; }
    @Override public boolean supportsMovies() { return false; }
    @Override public boolean supportsTvShows() { return true; }

    @Override public List<Models.ShowItem> movies(int page) { return List.of(); }

    @Override public List<Models.ShowItem> tvShows(int page) throws Exception {
        if (page < 1) return List.of();
        List<M3uPlaylist.Channel> channels = channels();
        int from = Math.min(channels.size(), (page - 1) * PAGE_SIZE);
        int to = Math.min(channels.size(), from + PAGE_SIZE);
        return map(channels.subList(from, to));
    }

    @Override public List<Models.ShowItem> search(String query, int page) throws Exception {
        if (query == null || query.isBlank() || page < 1) return List.of();
        String needle = query.strip().toLowerCase(java.util.Locale.ROOT);
        List<M3uPlaylist.Channel> matches = channels().stream()
                .filter(c -> contains(c.name(), needle) || contains(c.group(), needle))
                .toList();
        int from = Math.min(matches.size(), (page - 1) * PAGE_SIZE);
        int to = Math.min(matches.size(), from + PAGE_SIZE);
        return map(matches.subList(from, to));
    }

    @Override public List<Models.Episode> episodes(Models.ShowItem show) {
        return List.of(new Models.Episode(
                show.providerId(), 1, 1, "Señal en vivo", show.overview(), show.poster()));
    }

    @Override public List<Models.Server> servers(String providerItemId) {
        ChannelSource source = decode(providerItemId);
        if (source.url().isBlank()) return List.of();
        return List.of(new Models.Server(
                "live-" + Integer.toUnsignedString(providerItemId.hashCode()),
                "Stream directo",
                M3uStreamExtractor.SCHEME + providerItemId));
    }

    int channelCountForTest() throws Exception { return channels().size(); }

    private List<M3uPlaylist.Channel> channels() throws Exception {
        long now = System.currentTimeMillis();
        List<M3uPlaylist.Channel> current = cached;
        if (!current.isEmpty() && now - cachedAt < CACHE_MILLIS) return current;
        synchronized (this) {
            current = cached;
            if (!current.isEmpty() && now - cachedAt < CACHE_MILLIS) return current;
            String raw = http.get(playlistUrl, Map.of("Accept", "application/x-mpegURL,text/plain,*/*"));
            List<M3uPlaylist.Channel> parsed = M3uPlaylist.parse(raw);
            if (parsed.isEmpty()) throw new IllegalStateException(providerName + ": playlist vacía");
            cached = parsed;
            cachedAt = System.currentTimeMillis();
            return parsed;
        }
    }

    private List<Models.ShowItem> map(List<M3uPlaylist.Channel> channels) {
        ArrayList<Models.ShowItem> items = new ArrayList<>(channels.size());
        for (M3uPlaylist.Channel channel : channels) {
            String encoded = encode(channel);
            String overview = (channel.group() == null || channel.group().isBlank())
                    ? "Canal en vivo"
                    : "Canal en vivo · " + channel.group();
            items.add(new Models.ShowItem(
                    providerId + ":" + encoded,
                    encoded,
                    channel.name(),
                    overview,
                    null, null, null,
                    channel.logo(),
                    channel.logo(),
                    Models.ShowType.TV_SHOW,
                    providerId));
        }
        return List.copyOf(items);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    static String encode(M3uPlaylist.Channel channel) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("url", channel.url());
        map.put("name", channel.name());
        if (channel.logo() != null) map.put("logo", channel.logo());
        if (channel.group() != null) map.put("group", channel.group());
        if (channel.userAgent() != null) map.put("ua", channel.userAgent());
        if (channel.referrer() != null) map.put("ref", channel.referrer());
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Json.stringify(map).getBytes(StandardCharsets.UTF_8));
    }

    static ChannelSource decode(String encoded) {
        try {
            byte[] raw = Base64.getUrlDecoder().decode(encoded);
            Map<String, Object> map = Json.object(Json.parse(new String(raw, StandardCharsets.UTF_8)));
            return new ChannelSource(
                    Json.string(map.get("url")),
                    Json.string(map.get("name")),
                    blank(Json.string(map.get("ua"))),
                    blank(Json.string(map.get("ref"))));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Canal IPTV inválido.", ex);
        }
    }

    private static String blank(String value) { return value == null || value.isBlank() ? null : value; }

    record ChannelSource(String url, String name, String userAgent, String referrer) {}
}
