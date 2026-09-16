package dev.streamflix.desktop;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

final class Http {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36";

    private final HttpClient client;

    Http() {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .cookieHandler(cookies)
                .build();
    }

    String get(String url) throws IOException, InterruptedException {
        return request(HttpRequest.newBuilder(URI.create(url)).GET(), Map.of());
    }

    String get(String url, Map<String, String> headers) throws IOException, InterruptedException {
        return request(HttpRequest.newBuilder(URI.create(url)).GET(), headers);
    }

    byte[] getBytes(String url) throws IOException, InterruptedException {
        return getBytes(url, Map.of());
    }

    byte[] getBytes(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest req = withHeaders(HttpRequest.newBuilder(URI.create(url)).GET(), headers).build();
        HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        ensureSuccess(res.statusCode(), url);
        return res.body();
    }

    byte[] getBytesLegacy(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", headers.getOrDefault("User-Agent", USER_AGENT));
        headers.forEach(connection::setRequestProperty);
        int status = connection.getResponseCode();
        ensureSuccess(status, url);
        try (var in = connection.getInputStream()) { return in.readAllBytes(); }
        finally { connection.disconnect(); }
    }
    String postEmpty(String url, Map<String, String> headers) throws IOException, InterruptedException {
        return request(HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.noBody()), headers);
    }

    URI finalUri(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest req = withHeaders(HttpRequest.newBuilder(URI.create(url)).GET(), headers).build();
        HttpResponse<java.io.InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (java.io.InputStream ignored = res.body()) {
            ensureSuccess(res.statusCode(), url);
            return res.uri();
        }
    }

    String postJson(String url, String json, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json");
        return request(builder, headers);
    }

    String postForm(String url, Map<String, String> form, Map<String, String> headers) throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder();
        for (var entry : form.entrySet()) {
            if (!sb.isEmpty()) sb.append("&");
            sb.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString(), StandardCharsets.UTF_8))
                .header("Content-Type", "application/x-www-form-urlencoded");
        return request(builder, headers);
    }

    private String request(HttpRequest.Builder builder, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest req = withHeaders(builder, headers).build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(res.statusCode(), req.uri().toString());
        return res.body();
    }

    private HttpRequest.Builder withHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        builder.timeout(Duration.ofSeconds(45));
        if (!headers.containsKey("User-Agent")) builder.header("User-Agent", USER_AGENT);
        headers.forEach(builder::header);
        return builder;
    }

    private static void ensureSuccess(int status, String url) throws IOException {
        if (status < 200 || status >= 400) throw new IOException("HTTP " + status + " for " + url);
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
