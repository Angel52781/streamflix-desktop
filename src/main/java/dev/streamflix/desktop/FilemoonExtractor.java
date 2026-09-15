package dev.streamflix.desktop;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URI;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure JVM port of Streamflix Reborn's Filemoon extraction flow. */
final class FilemoonExtractor implements Extractor {
    private static final Set<String> HOSTS = Set.of(
            "filemoon.site", "filemoon.sx", "bf0skv.org", "bysejikuar.com",
            "moflix-stream.link", "bysezoxexe.com", "bysebuho.com", "bysekoze.com", "bysesayeveum.com"
    );
    private static final Pattern ID_PATTERN = Pattern.compile("/(e|d)/([a-zA-Z0-9]+)");
    private final Http http = new Http();
    private String deviceId = compactUuid();

    @Override public String name() { return "Filemoon"; }

    @Override public boolean supports(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            if (HOSTS.contains(host)) return true;
            return host.contains("filemoon") || host.contains("moflix-stream");
        } catch (Exception ignored) { return false; }
    }

    @Override public Models.Video extract(String link) throws Exception {
        Matcher matcher = ID_PATTERN.matcher(link);
        if (!matcher.find()) throw new IllegalArgumentException("Filemoon video id not found: " + link);
        String linkType = matcher.group(1);
        String videoId = matcher.group(2);
        URI input = URI.create(link);
        String currentDomain = input.getScheme() + "://" + input.getHost();

        Map<String, Object> details = Json.object(Json.parse(http.get(currentDomain + "/api/videos/" + videoId + "/embed/details")));
        String embedFrameUrl = Json.string(details.get("embed_frame_url"));
        if (embedFrameUrl.isBlank()) throw new IllegalStateException("Filemoon embed_frame_url missing");

        URI embedUri = URI.create(embedFrameUrl);
        String playbackDomain = embedUri.getScheme() + "://" + embedUri.getHost();
        Map<String, String> common = Map.of(
                "Referer", embedFrameUrl,
                "Origin", playbackDomain,
                "User-Agent", Http.USER_AGENT
        );

        Map<String, Object> challenge = Json.object(Json.parse(
                http.postEmpty(playbackDomain + "/api/videos/access/challenge", common)
        ));
        String challengeId = Json.string(challenge.get("challenge_id"));
        String nonce = Json.string(challenge.get("nonce"));
        if (challengeId.isBlank() || nonce.isBlank()) throw new IllegalStateException("Filemoon challenge incomplete");

        String viewerId = compactUuid();
        Attestation attestation = generateAttestation(nonce);

        Map<String, Object> attestPayload = new LinkedHashMap<>();
        attestPayload.put("viewer_id", viewerId);
        attestPayload.put("device_id", deviceId);
        attestPayload.put("challenge_id", challengeId);
        attestPayload.put("nonce", nonce);
        attestPayload.put("signature", attestation.signature());
        attestPayload.put("public_key", attestation.publicKey());
        attestPayload.put("client", linkedMap(
                "user_agent", Http.USER_AGENT,
                "architecture", "x86",
                "bitness", "64",
                "platform", "Windows",
                "platform_version", "10.0.0",
                "pixel_ratio", 1.0,
                "screen_width", 1920,
                "screen_height", 1080,
                "languages", List.of("en-US")
        ));
        attestPayload.put("storage", linkedMap(
                "cookie", viewerId,
                "local_storage", viewerId,
                "indexed_db", viewerId + ":" + deviceId,
                "cache_storage", viewerId + ":" + deviceId
        ));
        attestPayload.put("attributes", Map.of("entropy", "high"));

        Map<String, Object> attest = Json.object(Json.parse(
                http.postJson(playbackDomain + "/api/videos/access/attest", Json.stringify(attestPayload), common)
        ));
        String token = Json.string(attest.get("token"));
        if (token.isBlank()) throw new IllegalStateException("Filemoon attest token missing");
        String returnedViewer = Json.string(attest.get("viewer_id"));
        String returnedDevice = Json.string(attest.get("device_id"));
        if (!returnedViewer.isBlank()) viewerId = returnedViewer;
        if (!returnedDevice.isBlank()) deviceId = returnedDevice;
        Double confidence = Json.decimal(attest.get("confidence"));
        if (confidence == null) throw new IllegalStateException("Filemoon confidence missing");

        Map<String, Object> playbackPayload = Map.of(
                "fingerprint", linkedMap(
                        "token", token,
                        "viewer_id", viewerId,
                        "device_id", deviceId,
                        "confidence", confidence
                )
        );
        Map<String, String> playbackHeaders = new LinkedHashMap<>(common);
        playbackHeaders.put("X-Embed-Parent", "e".equals(linkType) ? link : "");
        Map<String, Object> playbackResponse = Json.object(Json.parse(
                http.postJson(playbackDomain + "/api/videos/" + videoId + "/embed/playback", Json.stringify(playbackPayload), playbackHeaders)
        ));
        Map<String, Object> playback = Json.object(playbackResponse.get("playback"));
        if (playback.isEmpty()) throw new IllegalStateException("Filemoon playback data missing");

        String decrypted = decryptPlayback(playback);
        Map<String, Object> payload = Json.object(Json.parse(decrypted));
        List<Object> sources = Json.array(payload.get("sources"));
        if (sources.isEmpty()) throw new IllegalStateException("Filemoon returned no sources");
        String source = Json.string(Json.object(sources.getFirst()).get("url"));
        if (source.isBlank()) throw new IllegalStateException("Filemoon source URL missing");

        return new Models.Video(source, Map.of(
                "Referer", embedFrameUrl,
                "Origin", playbackDomain,
                "User-Agent", Http.USER_AGENT
        ), List.of());
    }

    private static Attestation generateAttestation(String nonce) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) pair.getPublic();

        String x = base64Url(fixedUnsigned(pub.getW().getAffineX(), 32));
        String y = base64Url(fixedUnsigned(pub.getW().getAffineY(), 32));

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(pair.getPrivate());
        signer.update(nonce.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] rawSignature = derToRaw(signer.sign());

        Map<String, Object> jwk = linkedMap(
                "crv", "P-256",
                "ext", true,
                "key_ops", List.of("verify"),
                "kty", "EC",
                "x", x,
                "y", y
        );
        return new Attestation(base64Url(rawSignature), jwk);
    }

    private static String decryptPlayback(Map<String, Object> data) throws Exception {
        byte[] iv = decodeBase64Url(Json.string(data.get("iv")));
        byte[] payload = decodeBase64Url(Json.string(data.get("payload")));
        List<Object> parts = Json.array(data.get("key_parts"));
        if (parts.size() < 2) throw new IllegalStateException("Filemoon key_parts missing");
        byte[] p1 = decodeBase64Url(Json.string(parts.get(0)));
        byte[] p2 = decodeBase64Url(Json.string(parts.get(1)));
        byte[] key = new byte[p1.length + p2.length];
        System.arraycopy(p1, 0, key, 0, p1.length);
        System.arraycopy(p2, 0, key, p1.length, p2.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(payload), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] derToRaw(byte[] der) {
        int[] p = {0};
        if ((der[p[0]++] & 0xff) != 0x30) throw new IllegalArgumentException("Not DER sequence");
        readLength(der, p);
        if ((der[p[0]++] & 0xff) != 0x02) throw new IllegalArgumentException("Missing DER r");
        int rLen = readLength(der, p);
        byte[] r = Arrays.copyOfRange(der, p[0], p[0] + rLen); p[0] += rLen;
        if ((der[p[0]++] & 0xff) != 0x02) throw new IllegalArgumentException("Missing DER s");
        int sLen = readLength(der, p);
        byte[] s = Arrays.copyOfRange(der, p[0], p[0] + sLen);
        byte[] raw = new byte[64];
        copyUnsigned(r, raw, 0, 32);
        copyUnsigned(s, raw, 32, 32);
        return raw;
    }

    private static int readLength(byte[] bytes, int[] p) {
        int first = bytes[p[0]++] & 0xff;
        if ((first & 0x80) == 0) return first;
        int count = first & 0x7f, result = 0;
        for (int i = 0; i < count; i++) result = (result << 8) | (bytes[p[0]++] & 0xff);
        return result;
    }

    private static void copyUnsigned(byte[] src, byte[] dest, int offset, int size) {
        int start = 0;
        while (start < src.length - 1 && src[start] == 0) start++;
        int len = src.length - start;
        if (len > size) { start += len - size; len = size; }
        System.arraycopy(src, start, dest, offset + size - len, len);
    }

    private static byte[] fixedUnsigned(BigInteger n, int size) {
        byte[] b = n.toByteArray();
        byte[] out = new byte[size];
        copyUnsigned(b, out, 0, size);
        return out;
    }

    private static byte[] decodeBase64Url(String s) { return Base64.getUrlDecoder().decode(pad(s)); }
    private static String base64Url(byte[] b) { return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static String pad(String s) { return s + "=".repeat((4 - s.length() % 4) % 4); }
    private static String compactUuid() { return UUID.randomUUID().toString().replace("-", ""); }

    private static LinkedHashMap<String, Object> linkedMap(Object... pairs) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) out.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return out;
    }

    private record Attestation(String signature, Map<String, Object> publicKey) {}
}
