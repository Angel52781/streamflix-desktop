package dev.streamflix.desktop;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

final class AnimeUnityProvider implements Provider {
    private static final String ROOT = "https://www.animeunity.so";
    private final Http http = new Http();
    private String csrfToken = null;
    private long lastCsrfTime = 0L;

    @Override public String id() { return "animeunity"; }
    @Override public String name() { return "AnimeUnity"; }
    @Override public boolean supportsMovies() { return false; }
    @Override public boolean supportsTvShows() { return true; }

    @Override public List<Models.ShowItem> movies(int page) { return List.of(); }

    @Override public List<Models.ShowItem> tvShows(int page) throws Exception {
        ensureCsrf();
        int offset = Math.max(0, (page - 1) * 30);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", false);
        payload.put("type", false);
        payload.put("year", false);
        payload.put("order", false);
        payload.put("status", false);
        payload.put("genres", false);
        payload.put("offset", offset);
        payload.put("dubbed", false);
        payload.put("season", false);
        return queryAnimes(Json.stringify(payload));
    }

    @Override public List<Models.ShowItem> search(String query, int page) throws Exception {
        if (query == null || query.isBlank()) return List.of();
        ensureCsrf();
        int offset = Math.max(0, (page - 1) * 30);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", query.trim());
        payload.put("type", false);
        payload.put("year", false);
        payload.put("order", false);
        payload.put("status", false);
        payload.put("genres", false);
        payload.put("offset", offset);
        payload.put("dubbed", false);
        payload.put("season", false);
        return queryAnimes(Json.stringify(payload));
    }

    private List<Models.ShowItem> queryAnimes(String payload) throws Exception {
        Map<String, String> headers = new HashMap<>();
        if (csrfToken != null) headers.put("X-CSRF-TOKEN", csrfToken);
        headers.put("Referer", ROOT + "/archivio");

        String res = http.postJson(ROOT + "/archivio/get-animes", payload, headers);
        Map<String, Object> json = Json.object(Json.parse(res));
        List<Object> records = Json.array(json.get("records"));
        List<Models.ShowItem> out = new ArrayList<>();
        for (Object r : records) {
            Map<String, Object> item = Json.object(r);
            Number idNum = (Number) item.get("id");
            if (idNum == null) continue;
            int id = idNum.intValue();
            String slug = Json.string(item.get("slug"));
            String title = Json.string(item.get("title_eng"));
            if (title.isBlank()) title = Json.string(item.get("title"));
            if (title.isBlank()) title = slug;

            String showSlug = id + "-" + slug;
            String imageUrl = Json.string(item.get("imageurl"));
            String poster = formatImageUrl(imageUrl);
            out.add(new Models.ShowItem(showSlug, "show:" + showSlug, title, null, null, null, null, poster, null, Models.ShowType.TV_SHOW));
        }
        return out;
    }

    @Override public List<Models.Episode> episodes(Models.ShowItem show) throws Exception {
        String slug = show.providerId().replaceFirst("^show:", "");
        Document doc = Jsoup.parse(http.get(ROOT + "/anime/" + slug, headers()), ROOT);
        Element vp = doc.selectFirst("video-player");
        if (vp == null) return List.of();

        String epAttr = vp.attr("episodes");
        if (epAttr.isBlank()) return List.of();
        String decoded = URLDecoder.decode(epAttr, StandardCharsets.UTF_8);
        List<Object> epList = Json.array(Json.parse(decoded));

        List<Models.Episode> out = new ArrayList<>();
        for (int i = 0; i < epList.size(); i++) {
            Map<String, Object> ep = Json.object(epList.get(i));
            String epId = Json.string(ep.get("id"));
            String fileName = Json.string(ep.get("file_name"));
            String numStr = Json.string(ep.get("number"));
            int epNum = parseInt(numStr, i + 1);
            String title = extractEpisodeTitle(fileName, epNum);
            out.add(new Models.Episode("episode:" + slug + "/" + epId + "/" + epNum, 1, epNum, title, null, null));
        }
        return out;
    }

    @Override public List<Models.Server> servers(String providerItemId) throws Exception {
        String embedUrl = null;
        if (providerItemId.startsWith("episode:")) {
            String spec = providerItemId.substring("episode:".length());
            String[] parts = spec.split("/");
            String slug = parts[0];
            String epId = parts.length > 1 ? parts[1] : "";
            int epNum = parts.length > 2 ? parseInt(parts[2], 1) : 1;

            if (epNum == 1) {
                Document doc = Jsoup.parse(http.get(ROOT + "/anime/" + slug, headers()), ROOT);
                Element vp = doc.selectFirst("video-player");
                if (vp != null) embedUrl = vp.attr("embed_url");
            }

            if (embedUrl == null || embedUrl.isBlank()) {
                if (!epId.isBlank()) {
                    String raw = http.get(ROOT + "/embed-url/" + epId, headers()).trim();
                    if (!raw.isBlank() && raw.startsWith("http")) embedUrl = raw;
                }
            }
        } else {
            String slug = providerItemId.replaceFirst("^show:", "");
            Document doc = Jsoup.parse(http.get(ROOT + "/anime/" + slug, headers()), ROOT);
            Element vp = doc.selectFirst("video-player");
            if (vp != null) embedUrl = vp.attr("embed_url");
        }

        if (embedUrl == null || embedUrl.isBlank()) return List.of();
        if (embedUrl.contains("&amp;")) embedUrl = embedUrl.replace("&amp;", "&");
        return List.of(new Models.Server(embedUrl, "Vixcloud", embedUrl));
    }

    private synchronized void ensureCsrf() throws Exception {
        if (csrfToken != null && System.currentTimeMillis() - lastCsrfTime < 15 * 60 * 1000) {
            return;
        }
        String html = http.get(ROOT + "/archivio", headers());
        Matcher m = Pattern.compile("<meta\\s+name=[\"']csrf-token[\"']\\s+content=[\"']([^\"']+)[\"']").matcher(html);
        if (m.find()) {
            csrfToken = m.group(1);
            lastCsrfTime = System.currentTimeMillis();
        }
    }

    private static String formatImageUrl(String imageurl) {
        if (imageurl == null || imageurl.isBlank()) return null;
        if (imageurl.startsWith("http://") || imageurl.startsWith("https://")) return imageurl;
        String filename = imageurl.contains("/") ? imageurl.substring(imageurl.lastIndexOf('/') + 1) : imageurl;
        return "https://img.animeunity.so/anime/" + filename;
    }

    private static String extractEpisodeTitle(String fileName, int epNum) {
        if (fileName == null || fileName.isBlank()) return "Episodio " + epNum;
        return "Episodio " + epNum;
    }

    private static int parseInt(String val, int fallback) {
        if (val == null) return fallback;
        try {
            if (val.contains("-")) val = val.split("-")[0];
            return Integer.parseInt(val.trim());
        } catch (Exception ignored) { return fallback; }
    }

    private static Map<String, String> headers() {
        return Map.of("User-Agent", Http.USER_AGENT, "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
    }
}
