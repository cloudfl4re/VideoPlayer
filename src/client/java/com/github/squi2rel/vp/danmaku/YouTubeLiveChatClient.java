package com.github.squi2rel.vp.danmaku;

import com.github.squi2rel.vp.HttpProxyConfig;
import com.github.squi2rel.vp.VideoPlayerClient;
import com.github.squi2rel.vp.provider.VideoInfo;
import com.github.squi2rel.vp.provider.YouTubeProvider;
import com.github.squi2rel.vp.video.VideoParams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import static com.github.squi2rel.vp.VideoPlayerMain.LOGGER;

final class YouTubeLiveChatClient {
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final int MAX_PAGE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_POLL_BYTES = 4 * 1024 * 1024;
    private static final int MAX_SEEN_IDS = 2048;
    private static final int MAX_FAILURE_DELAY_SECONDS = 30;

    private final URI watchUri;
    private final Consumer<DanmakuEntry> receiver;
    private final HttpClient httpClient;
    private final Set<String> seenIds = new LinkedHashSet<>();
    private volatile boolean stopped = true;
    private volatile Thread worker;
    private volatile InputStream activeBody;
    private boolean sessionEstablished;

    YouTubeLiveChatClient(VideoInfo info, Consumer<DanmakuEntry> receiver) {
        this.watchUri = watchUri(info);
        this.receiver = receiver;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL);
        String proxy = VideoPlayerClient.config == null ? "" : VideoPlayerClient.config.nativeDownloadProxy;
        this.httpClient = HttpProxyConfig.parse(proxy).configure(builder).build();
    }

    static boolean canResolve(VideoInfo info) {
        return info != null
                && VideoParams.hasYouTubeLiveChat(info.params())
                && YouTubeProvider.isYouTubeRawPath(info.rawPath());
    }

    synchronized void start() {
        Thread current = worker;
        if (current != null && current.isAlive()) return;
        stopped = false;
        Thread created = new Thread(this::run, "VideoPlayer-youtube-live-chat");
        created.setDaemon(true);
        worker = created;
        created.start();
    }

    synchronized void stop() {
        stopped = true;
        InputStream body = activeBody;
        activeBody = null;
        if (body != null) {
            try {
                body.close();
            } catch (IOException ignored) {
            }
        }
        Thread current = worker;
        worker = null;
        if (current != null) current.interrupt();
    }

    private void run() {
        long failures = 0;
        while (!stopped && !Thread.currentThread().isInterrupted()) {
            try {
                runSession();
                failures = 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (stopped) return;
                failures = sessionEstablished ? 1 : failures + 1;
                if (failures == 1 || failures % 5 == 0) {
                    LOGGER.warn("YouTube live chat connection failed: {}", failureMessage(e));
                } else {
                    LOGGER.debug("YouTube live chat retry failed: {}", failureMessage(e));
                }
                try {
                    pause(retryDelayMillis(failures));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void runSession() throws Exception {
        sessionEstablished = false;
        YouTubeLiveChatProtocol.Bootstrap bootstrap = YouTubeLiveChatProtocol.parseBootstrap(fetchPage());
        YouTubeLiveChatProtocol.PollResult baseline = poll(bootstrap, bootstrap.continuation());
        remember(baseline);
        YouTubeLiveChatProtocol.PollResult current = baseline;
        if (!baseline.liveFilterSelected()) {
            String liveFilter = baseline.liveFilterContinuation();
            if (liveFilter == null || liveFilter.isBlank()) {
                throw new IOException("YouTube Live chat filter is unavailable");
            }
            current = poll(bootstrap, liveFilter);
            remember(current);
        }
        String continuation = current.continuation();
        if (continuation == null || continuation.isBlank()) {
            throw new IOException("YouTube live chat continuation ended");
        }
        sessionEstablished = true;
        pause(YouTubeLiveChatProtocol.pollingDelay(current.timeoutMs()));
        while (!stopped && !Thread.currentThread().isInterrupted()) {
            YouTubeLiveChatProtocol.PollResult result = poll(bootstrap, continuation);
            for (YouTubeLiveChatProtocol.ChatMessage message : result.messages()) {
                boolean unseen = remember(message.id());
                if (unseen && !stopped) {
                    receiver.accept(DanmakuEntry.live(1, 25, 0xFFFFFF, message.displayText()));
                }
            }
            continuation = result.continuation();
            if (continuation == null || continuation.isBlank()) {
                throw new IOException("YouTube live chat continuation ended");
            }
            pause(YouTubeLiveChatProtocol.pollingDelay(result.timeoutMs()));
        }
    }

    private void remember(YouTubeLiveChatProtocol.PollResult result) {
        if (result == null || result.messages() == null) return;
        for (YouTubeLiveChatProtocol.ChatMessage message : result.messages()) {
            remember(message.id());
        }
    }

    private String fetchPage() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(watchUri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "SOCS=CAI")
                .GET()
                .build();
        return send(request, MAX_PAGE_BYTES);
    }

    private YouTubeLiveChatProtocol.PollResult poll(YouTubeLiveChatProtocol.Bootstrap bootstrap,
                                                    String continuation) throws Exception {
        String key = URLEncoder.encode(bootstrap.apiKey(), StandardCharsets.UTF_8);
        URI uri = URI.create("https://www.youtube.com/youtubei/v1/live_chat/get_live_chat?key=" + key + "&prettyPrint=false");
        String body = YouTubeLiveChatProtocol.requestBody(bootstrap, continuation).toString();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Content-Type", "application/json")
                .header("Origin", "https://www.youtube.com")
                .header("Referer", watchUri.toString())
                .header("X-YouTube-Client-Name", "1")
                .header("X-YouTube-Client-Version", bootstrap.clientVersion())
                .header("Cookie", "SOCS=CAI")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return YouTubeLiveChatProtocol.parsePoll(send(request, MAX_POLL_BYTES));
    }

    private String send(HttpRequest request, int maxBytes) throws Exception {
        ensureRunning();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        InputStream body = response.body();
        activeBody = body;
        try (body) {
            ensureRunning();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("YouTube returned HTTP " + response.statusCode());
            }
            return new String(readLimited(body, maxBytes), StandardCharsets.UTF_8);
        } finally {
            if (activeBody == body) activeBody = null;
        }
    }

    private void ensureRunning() throws InterruptedException {
        if (!stopped && !Thread.currentThread().isInterrupted()) return;
        throw new InterruptedException("YouTube live chat stopped");
    }

    private byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            if (total > maxBytes - read) throw new IOException("YouTube response is too large");
            output.write(buffer, 0, read);
            total += read;
        }
        return output.toByteArray();
    }

    private boolean remember(String id) {
        if (id == null || id.isBlank()) return false;
        if (!seenIds.add(id)) return false;
        while (seenIds.size() > MAX_SEEN_IDS) {
            Iterator<String> iterator = seenIds.iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
        return true;
    }

    private void pause(long millis) throws InterruptedException {
        if (stopped) return;
        Thread.sleep(Math.max(1L, millis));
    }

    private static long retryDelayMillis(long failures) {
        long seconds = 1L << (int) Math.min(5L, Math.max(0L, failures - 1L));
        return Math.min(MAX_FAILURE_DELAY_SECONDS, seconds) * 1000L;
    }

    private static URI watchUri(VideoInfo info) {
        if (!canResolve(info)) throw new IllegalArgumentException("YouTube live chat source is unavailable");
        String id = videoId(info.rawPath());
        if (id.isBlank()) throw new IllegalArgumentException("YouTube video id is missing");
        return URI.create("https://www.youtube.com/watch?v="
                + URLEncoder.encode(id, StandardCharsets.UTF_8) + "&hl=en");
    }

    private static String videoId(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) return "";
        if (!value.contains("://")) value = "https://" + value;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (host.equals("youtu.be")) return firstPathSegment(path);
            if (path.equals("/watch")) return queryValue(uri.getRawQuery(), "v");
            for (String prefix : new String[]{"/shorts/", "/live/", "/embed/", "/v/"}) {
                if (path.startsWith(prefix)) return firstPathSegment(path.substring(prefix.length() - 1));
            }
        } catch (RuntimeException ignored) {
        }
        return "";
    }

    private static String firstPathSegment(String path) {
        String value = path == null ? "" : path;
        while (value.startsWith("/")) value = value.substring(1);
        int slash = value.indexOf('/');
        return slash < 0 ? value : value.substring(0, slash);
    }

    private static String queryValue(String query, String key) {
        if (query == null || query.isBlank()) return "";
        for (String part : query.split("&")) {
            int equals = part.indexOf('=');
            String name = equals < 0 ? part : part.substring(0, equals);
            if (!name.equals(key)) continue;
            String value = equals < 0 ? "" : part.substring(equals + 1);
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        return "";
    }

    private static String failureMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return error.getClass().getSimpleName();
        String normalized = message.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256);
    }
}
