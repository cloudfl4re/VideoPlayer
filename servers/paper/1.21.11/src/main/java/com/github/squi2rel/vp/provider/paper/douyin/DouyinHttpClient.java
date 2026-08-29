package com.github.squi2rel.vp.provider.paper.douyin;

import com.github.squi2rel.vp.HttpProxyConfig;
import com.github.squi2rel.vp.provider.MediaAddressPolicy;
import com.github.squi2rel.vp.provider.paper.SubmittedUrl;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

final class DouyinHttpClient implements DouyinUrlResolver.RedirectFollower, AutoCloseable {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final URI TTWID_URI = URI.create("https://ttwid.bytedance.com/ttwid/union/register/");
    private static final String TTWID_BODY = "{\"region\":\"cn\",\"aid\":1768,\"needFid\":false,\"service\":\"www.ixigua.com\",\"migrate_info\":{\"ticket\":\"\",\"source\":\"node\"},\"cbUrlProtocol\":\"https\",\"union\":true}";
    private static final String DETAIL_ENDPOINT = "https://www.douyin.com/aweme/v1/web/aweme/detail/?";
    private static final int MAX_DOCUMENT_BYTES = 4 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private static final long SESSION_TTL_MILLIS = Duration.ofMinutes(30).toMillis();
    private static final char[] RANDOM_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final char[] VERIFY_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private final Exchange exchange;
    private final SecureRandom random;
    private final ABogusSigner signer;
    private final LongSupplier currentTimeMillis;
    private final boolean proxyEnabled;
    private final Object sessionLock = new Object();
    private volatile Session session;

    DouyinHttpClient(HttpProxyConfig proxy) {
        this(new JavaExchange(proxy), new SecureRandom(), new ABogusSigner(), System::currentTimeMillis, proxy.enabled());
    }

    DouyinHttpClient(Exchange exchange, SecureRandom random, ABogusSigner signer,
                     LongSupplier currentTimeMillis, boolean proxyEnabled) {
        this.exchange = exchange;
        this.random = random;
        this.signer = signer;
        this.currentTimeMillis = currentTimeMillis;
        this.proxyEnabled = proxyEnabled;
    }

    JsonObject videoDetail(String id) throws Exception {
        RiskControlException failure = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            Session current = session();
            try {
                return requestVideoDetail(id, current);
            } catch (RiskControlException error) {
                failure = error;
                invalidate(current);
            }
        }
        throw failure == null ? new RiskControlException() : failure;
    }

    String livePage(String roomId) throws Exception {
        String nonce = randomHex(21);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://live.douyin.com/" + roomId))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Cookie", "__ac_nonce=" + nonce)
                .GET()
                .build();
        Response response = exchange.send(request, MAX_DOCUMENT_BYTES);
        requireSuccess(response, "Douyin live page");
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    URI playableVideo(List<String> urls, String referer) throws Exception {
        if (urls == null) return null;
        for (String value : urls) {
            URI candidate = mediaUri(value);
            if (candidate == null || SubmittedUrl.host(candidate, "www.douyin.com", "douyin.com")) continue;
            URI verified = probe(candidate, referer, 2);
            if (verified != null) return verified;
        }
        for (String value : urls) {
            URI candidate = mediaUri(value);
            if (candidate == null) continue;
            URI verified = probe(candidate, referer, 2);
            if (verified != null) return verified;
        }
        return null;
    }

    URI liveMedia(String value) {
        return mediaUri(value);
    }

    @Override
    public URI follow(URI submitted) throws Exception {
        URI current = submitted;
        for (int count = 0; count <= MAX_REDIRECTS; count++) {
            if (DouyinUrlResolver.classify(current) != null && !SubmittedUrl.host(current, "v.douyin.com")) return current;
            if (!DouyinUrlResolver.trustedRedirect(current)) throw new IllegalStateException("Douyin short link redirected to an untrusted host");
            Response response = exchange.send(redirectRequest(current, true), -1);
            String location = response.first("Location");
            if (location == null && (response.status() == 400 || response.status() == 403 || response.status() == 405)) {
                response = exchange.send(redirectRequest(current, false), -1);
                location = response.first("Location");
            }
            if (location == null) throw new IllegalStateException("Douyin short link did not redirect");
            current = SubmittedUrl.normalize(current.resolve(location));
            if (!DouyinUrlResolver.trustedRedirect(current)) {
                throw new IllegalStateException("Douyin short link redirected to an untrusted host");
            }
        }
        throw new IllegalStateException("Douyin short link exceeded the redirect limit");
    }

    @Override
    public void close() {
        session = null;
        try {
            exchange.close();
        } catch (Exception ignored) {
        }
    }

    private JsonObject requestVideoDetail(String id, Session current) throws Exception {
        LinkedHashMap<String, String> params = detailParams(id, current);
        String query = query(params);
        String signature = signer.sign(query);
        URI uri = URI.create(DETAIL_ENDPOINT + query + "&a_bogus=" + encode(signature));
        String referer = "https://www.douyin.com/video/" + id;
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", referer)
                .header("Origin", "https://www.douyin.com")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Dest", "empty")
                .header("Cookie", current.cookie())
                .GET()
                .build();
        Response response = exchange.send(request, MAX_DOCUMENT_BYTES);
        if (response.status() < 200 || response.status() >= 300 || response.body().length == 0) {
            throw new RiskControlException();
        }
        try {
            JsonObject root = JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (integer(root, "status_code", -1) != 0) throw new IllegalStateException("Douyin rejected the video detail request");
            JsonObject detail = object(root, "aweme_detail");
            if (detail == null) throw new RiskControlException();
            return detail;
        } catch (RiskControlException | IllegalStateException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new RiskControlException();
        }
    }

    private Session session() throws Exception {
        Session current = session;
        long now = currentTimeMillis.getAsLong();
        if (current != null && now < current.expiresAt()) return current;
        synchronized (sessionLock) {
            current = session;
            now = currentTimeMillis.getAsLong();
            if (current != null && now < current.expiresAt()) return current;
            session = bootstrap(now);
            return session;
        }
    }

    private Session bootstrap(long now) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(TTWID_URI)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(TTWID_BODY, StandardCharsets.UTF_8))
                .build();
        Response response = exchange.send(request, 64 * 1024);
        requireSuccess(response, "Douyin anonymous session");
        String ttwid = cookie(response.headers("Set-Cookie"), "ttwid");
        if (ttwid.isBlank()) throw new IllegalStateException("Douyin anonymous session did not return a token");
        String msToken = randomString(182) + "==";
        String verify = verifyFp(now);
        return new Session(ttwid, msToken, verify, now + SESSION_TTL_MILLIS);
    }

    private void invalidate(Session expected) {
        synchronized (sessionLock) {
            if (session == expected) session = null;
        }
    }

    private URI probe(URI initial, String referer, int maximumRedirects) throws Exception {
        URI current = initial;
        for (int count = 0; count <= maximumRedirects; count++) {
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", referer)
                    .header("Range", "bytes=0-0")
                    .GET()
                    .build();
            Response response;
            try {
                response = exchange.send(request, -1);
            } catch (Exception error) {
                return null;
            }
            if ((response.status() == 200 || response.status() == 206) && playableContentType(response.first("Content-Type"))) {
                return current;
            }
            String location = response.first("Location");
            if (response.status() < 300 || response.status() >= 400 || location == null) return null;
            current = mediaUri(current.resolve(location).toString());
            if (current == null) return null;
        }
        return null;
    }

    private URI mediaUri(String value) {
        if (value == null || value.isBlank() || value.length() > 8192) return null;
        try {
            URI parsed = URI.create(value.trim());
            if (parsed.getScheme() == null || parsed.getHost() == null || parsed.getUserInfo() != null || parsed.getPort() >= 0) return null;
            String scheme = parsed.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) return null;
            URI normalized = new URI("https", null, parsed.getHost().toLowerCase(Locale.ROOT), -1,
                    parsed.getRawPath(), parsed.getRawQuery(), null);
            return MediaAddressPolicy.isAllowedForDownload(normalized.toString(), proxyEnabled) ? normalized : null;
        } catch (Exception error) {
            return null;
        }
    }

    private HttpRequest redirectRequest(URI uri, boolean head) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,*/*");
        if (head) return builder.method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        return builder.header("Range", "bytes=0-0").GET().build();
    }

    private LinkedHashMap<String, String> detailParams(String id, Session current) {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("device_platform", "webapp");
        params.put("aid", "6383");
        params.put("channel", "channel_pc_web");
        params.put("pc_client_type", "1");
        params.put("publish_video_strategy_type", "2");
        params.put("pc_libra_divert", "Windows");
        params.put("version_code", "290100");
        params.put("version_name", "29.1.0");
        params.put("cookie_enabled", "true");
        params.put("screen_width", "1920");
        params.put("screen_height", "1080");
        params.put("browser_language", "zh-CN");
        params.put("browser_platform", "Win32");
        params.put("browser_name", "Edge");
        params.put("browser_version", "130.0.0.0");
        params.put("browser_online", "true");
        params.put("engine_name", "Blink");
        params.put("engine_version", "130.0.0.0");
        params.put("os_name", "Windows");
        params.put("os_version", "10");
        params.put("cpu_core_num", "12");
        params.put("device_memory", "8");
        params.put("platform", "PC");
        params.put("downlink", "10");
        params.put("effective_type", "4g");
        params.put("round_trip_time", "100");
        params.put("msToken", current.msToken());
        params.put("verifyFp", current.verifyFp());
        params.put("fp", current.verifyFp());
        params.put("aweme_id", id);
        return params;
    }

    private String verifyFp(long millis) {
        String base36 = Long.toString(millis, 36);
        char[] value = new char[36];
        value[8] = value[13] = value[18] = value[23] = '_';
        value[14] = '4';
        for (int index = 0; index < value.length; index++) {
            if (value[index] != 0) continue;
            int selected = random.nextInt(VERIFY_ALPHABET.length);
            if (index == 19) selected = (selected & 3) | 8;
            value[index] = VERIFY_ALPHABET[selected];
        }
        return "verify_" + base36 + "_" + new String(value);
    }

    private String randomString(int length) {
        char[] value = new char[length];
        for (int index = 0; index < value.length; index++) value[index] = RANDOM_ALPHABET[random.nextInt(RANDOM_ALPHABET.length)];
        return new String(value);
    }

    private String randomHex(int length) {
        char[] value = new char[length];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int index = 0; index < value.length; index++) value[index] = alphabet[random.nextInt(alphabet.length)];
        return new String(value);
    }

    private static String query(LinkedHashMap<String, String> params) {
        StringBuilder value = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!value.isEmpty()) value.append('&');
            value.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return value.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static void requireSuccess(Response response, String operation) {
        if (response.status() < 200 || response.status() >= 300) {
            throw new IllegalStateException(operation + " returned HTTP " + response.status());
        }
    }

    private static boolean playableContentType(String value) {
        if (value == null || value.isBlank()) return true;
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.startsWith("video/")
                || normalized.startsWith("audio/")
                || normalized.startsWith("application/octet-stream")
                || normalized.startsWith("binary/octet-stream");
    }

    private static String cookie(List<String> headers, String name) {
        String prefix = name + "=";
        for (String header : headers) {
            for (String part : header.split(";")) {
                String value = part.trim();
                if (value.startsWith(prefix)) return value.substring(prefix.length());
            }
        }
        return "";
    }

    private static JsonObject object(JsonObject parent, String name) {
        if (parent == null || !parent.has(name)) return null;
        JsonElement value = parent.get(name);
        return value == null || value.isJsonNull() || !value.isJsonObject() ? null : value.getAsJsonObject();
    }

    private static int integer(JsonObject parent, String name, int fallback) {
        if (parent == null || !parent.has(name)) return fallback;
        try {
            return parent.get(name).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    record Response(int status, Map<String, List<String>> headers, byte[] body) {
        String first(String name) {
            List<String> values = headers(name);
            return values.isEmpty() ? null : values.get(0);
        }

        List<String> headers(String name) {
            if (headers == null || name == null) return List.of();
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
            }
            return List.of();
        }
    }

    @FunctionalInterface
    interface Exchange extends AutoCloseable {
        Response send(HttpRequest request, int maximumBytes) throws Exception;

        @Override
        default void close() {
        }
    }

    private static final class JavaExchange implements Exchange {
        private final HttpClient client;

        JavaExchange(HttpProxyConfig proxy) {
            this.client = proxy.configure(HttpClient.newBuilder())
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
        }

        @Override
        public Response send(HttpRequest request, int maximumBytes) throws Exception {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                byte[] body = maximumBytes < 0 ? new byte[0] : readLimited(input, maximumBytes);
                return new Response(response.statusCode(), response.headers().map(), body);
            }
        }

        private static byte[] readLimited(InputStream input, int maximumBytes) throws Exception {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 64 * 1024));
            byte[] buffer = new byte[64 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                if (total > maximumBytes - read) throw new IllegalStateException("Douyin response exceeds the size limit");
                output.write(buffer, 0, read);
                total += read;
            }
            return output.toByteArray();
        }

        @Override
        public void close() {
            client.shutdownNow();
        }
    }

    private record Session(String ttwid, String msToken, String verifyFp, long expiresAt) {
        String cookie() {
            return "ttwid=" + ttwid + "; msToken=" + msToken + "; s_v_web_id=" + verifyFp;
        }
    }

    private static final class RiskControlException extends Exception {
    }
}
