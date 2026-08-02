package com.github.squi2rel.vp.provider;

import com.github.squi2rel.vp.i18n.VpTranslation;
import com.github.squi2rel.vp.provider.youtube.YouTubeQuality;
import com.github.squi2rel.vp.video.VideoParams;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class YouTubeProvider implements IVideoProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("videoplayer");
    private static final String[] YT_DLP_CANDIDATES = {"yt-dlp", "yt-dlp.exe"};
    private static final long YT_DLP_TIMEOUT_SECONDS = 30;
    private static final int MAX_YTDLP_OUTPUT_BYTES = 16 * 1024 * 1024;
    private static final int MAX_SUBTITLE_OPTIONS = 24;
    private static final int MAX_SUBTITLE_PARAM_BYTES = 7800;
    private static final Cache<String, List<Integer>> AVAILABLE_QUALITIES = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).maximumSize(1024).build();
    private static final AtomicLong RESOLUTION_IDS = new AtomicLong();
    private static volatile String configuredProxy = "";
    private static volatile String configuredYtdlPath = "";
    private static volatile String configuredCookiesFile = "";
    private static volatile String configuredCookiesFromBrowser = "";
    private static volatile MissingYtDlpHandler missingYtDlpHandler = () -> "";
    private final ProcessStarter processStarter;

    public YouTubeProvider() {
        this(new DefaultProcessStarter());
    }

    YouTubeProvider(ProcessStarter processStarter) {
        this.processStarter = Objects.requireNonNull(processStarter);
    }

    public static void configureProxy(String proxy) {
        configuredProxy = proxy == null ? "" : proxy.trim();
    }

    public static void configureYtdlPath(String ytdlPath) {
        configuredYtdlPath = ytdlPath == null ? "" : ytdlPath.trim();
    }

    public static void configureCookies(String cookiesFile, String cookiesFromBrowser) {
        configuredCookiesFile = safeSetting(cookiesFile);
        configuredCookiesFromBrowser = safeSetting(cookiesFromBrowser);
    }

    public static void configureCookiesFile(String cookiesFile) {
        configureCookies(cookiesFile, configuredCookiesFromBrowser);
    }

    public static void configureCookiesFromBrowser(String cookiesFromBrowser) {
        configureCookies(configuredCookiesFile, cookiesFromBrowser);
    }

    public static void configureMissingYtdlHandler(MissingYtDlpHandler handler) {
        missingYtDlpHandler = handler == null ? () -> "" : handler;
    }

    public static void configureMissingYtDlpHandler(MissingYtDlpHandler handler) {
        configureMissingYtdlHandler(handler);
    }

    public static void configureYtdlInstaller(MissingYtDlpHandler handler) {
        configureMissingYtdlHandler(handler);
    }

    public static void configureYtDlpInstaller(MissingYtDlpHandler handler) {
        configureMissingYtdlHandler(handler);
    }

    @Override
    public @Nullable CompletableFuture<VideoInfo> from(String str, IProviderSource source) {
        String url = normalizedUrl(str);
        if (url == null) return null;
        long resolutionId = RESOLUTION_IDS.incrementAndGet();
        String threadName = "VideoPlayer-yt-dlp-" + resolutionId;
        ResolutionCancellation cancellation = new ResolutionCancellation();
        ResolutionFuture future = new ResolutionFuture(cancellation);
        Thread worker = new Thread(() -> {
            try {
                LOGGER.info("YouTube resolution #{} started for {} (heightLimit={})",
                        resolutionId, VideoProviders.redactedSource(url), source.youtubeHeightLimit());
                VideoInfo info = resolve(url, source, cancellation, resolutionId, threadName);
                if (!future.isCancelled()) {
                    future.complete(info);
                    if (info == null) {
                        LOGGER.warn("YouTube resolution #{} completed without a playable result for {}",
                                resolutionId, VideoProviders.redactedSource(url));
                    }
                }
            } catch (CancellationException e) {
                LOGGER.info("YouTube resolution #{} cancelled for {}", resolutionId, VideoProviders.redactedSource(url));
                future.cancel(false);
            } catch (Throwable e) {
                LOGGER.warn("YouTube resolution #{} failed for {}", resolutionId, VideoProviders.redactedSource(url), e);
                future.completeExceptionally(e);
            }
        }, threadName);
        worker.setDaemon(true);
        future.setWorker(worker);
        worker.start();
        return future;
    }

    private VideoInfo resolve(String url, IProviderSource source, ResolutionCancellation cancellation,
                              long resolutionId, String threadName) {
        YtDlpResult result;
        try {
            result = runYtDlp(url, source.youtubeHeightLimit(), cancellation, resolutionId, threadName);
        } catch (CancellationException e) {
            throw e;
        } catch (YtDlpMissingException e) {
            LOGGER.warn("Unable to start yt-dlp; no usable executable was found", e);
            source.reply(VpTranslation.of("message.videoplayer.youtube_ytdlp_missing", "yt-dlp is unavailable; install it or enable automatic download"));
            return clientResolvableFallback(source, url, "", 0L, false);
        } catch (Exception e) {
            LOGGER.warn("Failed to run yt-dlp for YouTube metadata: {}", failureSummary(e));
            replyFailure(source, classifyFailure(e.getMessage()));
            return clientResolvableFallback(source, url, "", 0L, false);
        }

        cancellation.throwIfCancelled();
        if (result.exitCode != 0) {
            String failure = classifyFailure(result.stderr);
            LOGGER.warn("yt-dlp resolution #{} failed; exit={}, reason={}",
                    resolutionId, result.exitCode, failure);
            replyFailure(source, failure);
            return clientResolvableFallback(source, url, "", 0L, false);
        }

        JsonObject metadata;
        try {
            metadata = JsonParser.parseString(result.stdout).getAsJsonObject();
        } catch (RuntimeException e) {
            LOGGER.warn("yt-dlp resolution #{} returned invalid YouTube metadata JSON; stdoutBytes={}, stderr={}",
                    resolutionId, utf8Bytes(result.stdout), summarizeOutput(result.stderr), failureSummary(e));
            replyFailure(source, "unknown");
            return clientResolvableFallback(source, url, "", 0L, false);
        }

        cancellation.throwIfCancelled();
        VideoInfo info = videoInfo(metadata, url, source);
        if (info != null) {
            LOGGER.info("YouTube resolution #{} parsed id={} title='{}' durationMs={} stream={} params={}",
                    resolutionId, string(metadata, "id", "<unknown>"),
                    summarizeOutput(info.name()), info.durationMs(),
                    !info.path().isBlank(), info.params().length);
        }
        return info;
    }

    static VideoInfo videoInfo(JsonObject metadata, String url, IProviderSource source) {
        updateAvailableQualities(url, metadata);

        boolean live = isLive(metadata);
        long durationMs = durationMs(metadata);
        if (durationMs <= 0 && !live) {
            source.reply(VpTranslation.of("message.videoplayer.youtube_duration_missing", "Failed to get YouTube duration"));
            return clientResolvableFallback(source, url, string(metadata, "title", ""), durationMs, false);
        }

        String id = string(metadata, "id", idFromUrl(url));
        String title = string(metadata, "title", id == null ? "YouTube Video" : "YouTube " + id);
        ResolvedMedia media = resolvedMedia(metadata);
        if (media == null) {
            source.reply(VpTranslation.of("message.videoplayer.youtube_stream_missing", "Failed to get a playable YouTube stream; the client will retry with its local yt-dlp"));
            return clientResolvableFallback(source, url, title, durationMs, live);
        }
        return new VideoInfo(source.name(), title, media.videoUrl, url, media.expire, !live,
                params(metadata, url, media), durationMs);
    }

    static VideoInfo clientResolvableFallback(IProviderSource source, String rawUrl, String title,
                                              long durationMs, boolean live) {
        String safeTitle = title == null || title.isBlank() ? "YouTube Video" : title;
        return new VideoInfo(source.name(), safeTitle, "", rawUrl, -1, !live, new String[0], durationMs);
    }

    private static String[] params(JsonObject metadata, String referer, ResolvedMedia media) {
        ArrayList<String> params = new ArrayList<>();
        addParameter(params, VideoParams.PARAM_YOUTUBE + "=true");
        if (hasLiveChat(metadata)) addParameter(params, VideoParams.PARAM_YOUTUBE_LIVE_CHAT + "=true");
        addParameter(params, "ytdl=no");
        JsonObject headers = media.headers == null ? object(metadata, "http_headers") : media.headers;
        String userAgent = string(headers, "User-Agent", string(headers, "user-agent", ""));
        String mediaReferer = string(headers, "Referer", string(headers, "referer", referer));
        if (!userAgent.isBlank()) addParameter(params, "user-agent=" + userAgent);
        if (!mediaReferer.isBlank()) addParameter(params, "referrer=" + mediaReferer);
        if (!media.audioUrl.isBlank()) {
            addParameter(params, "audio-file=" + media.audioUrl);
            addParameter(params, ":input-slave=" + media.audioUrl);
        } else if (!media.hasAudio) {
            addParameter(params, VideoParams.PARAM_VIDEO_ONLY + "=true");
        }
        for (SubtitleCandidate candidate : subtitleCandidates(metadata, referer)) {
            String param = VideoParams.subtitleParam(candidate.key(), candidate.label(), candidate.language(), candidate.url(), referer, candidate.automatic());
            if (param.getBytes(StandardCharsets.UTF_8).length <= MAX_SUBTITLE_PARAM_BYTES) addParameter(params, param);
        }
        return params.toArray(String[]::new);
    }

    private static boolean addParameter(List<String> params, String parameter) {
        if (parameter == null || parameter.isBlank() || params.size() >= VideoInfo.MAX_PARAMS) return false;
        int parameterBytes = utf8Bytes(parameter);
        if (parameterBytes > VideoInfo.MAX_TOTAL_PARAM_BYTES) return false;
        int totalBytes = 0;
        for (String existing : params) {
            totalBytes += utf8Bytes(existing);
        }
        if (totalBytes > VideoInfo.MAX_TOTAL_PARAM_BYTES - parameterBytes) return false;
        params.add(parameter);
        return true;
    }

    private static ResolvedMedia resolvedMedia(JsonObject metadata) {
        JsonArray formats = array(metadata, "requested_formats");
        JsonObject video = null;
        JsonObject audio = null;
        if (formats != null) {
            for (JsonElement element : formats) {
                if (!element.isJsonObject()) continue;
                JsonObject format = element.getAsJsonObject();
                String formatUrl = string(format, "url", "");
                if (formatUrl.isBlank()) continue;
                boolean hasVideo = hasCodec(format, "vcodec");
                boolean hasAudio = hasCodec(format, "acodec");
                if (hasVideo && hasAudio) return media(formatUrl, "", true, object(format, "http_headers"));
                if (hasVideo && video == null) video = format;
                if (hasAudio && audio == null) audio = format;
            }
        }
        if (video != null) {
            String videoUrl = string(video, "url", "");
            String audioUrl = audio == null ? "" : string(audio, "url", "");
            return media(videoUrl, audioUrl, !audioUrl.isBlank(), object(video, "http_headers"));
        }
        String directUrl = string(metadata, "url", "");
        if (directUrl.isBlank()) return null;
        return media(directUrl, "", hasCodec(metadata, "acodec"), object(metadata, "http_headers"));
    }

    private static ResolvedMedia media(String videoUrl, String audioUrl, boolean hasAudio, JsonObject headers) {
        long expire = mediaExpiry(videoUrl, audioUrl);
        return new ResolvedMedia(videoUrl, audioUrl == null ? "" : audioUrl, hasAudio, headers, expire);
    }

    static long mediaExpiry(String videoUrl, String audioUrl) {
        long videoExpire = urlExpiry(videoUrl);
        long audioExpire = urlExpiry(audioUrl);
        long expire;
        if (videoExpire > 0 && audioExpire > 0) {
            expire = Math.min(videoExpire, audioExpire);
        } else {
            expire = Math.max(videoExpire, audioExpire);
        }
        return expire < 0 ? System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(20) : expire;
    }

    static boolean isLive(JsonObject metadata) {
        if (metadata == null) return false;
        JsonElement live = metadata.get("is_live");
        if (live != null && live.isJsonPrimitive()) {
            try {
                if (live.getAsBoolean()) return true;
            } catch (RuntimeException ignored) {
            }
        }
        String status = string(metadata, "live_status", "").toLowerCase(Locale.ROOT);
        return status.equals("is_live") || status.equals("is_upcoming");
    }

    static boolean hasLiveChat(JsonObject metadata) {
        if (metadata == null) return false;
        JsonElement live = metadata.get("is_live");
        if (live != null && live.isJsonPrimitive()) {
            try {
                if (live.getAsBoolean()) return true;
            } catch (RuntimeException ignored) {
            }
        }
        return string(metadata, "live_status", "").equalsIgnoreCase("is_live");
    }

    private static boolean hasCodec(JsonObject format, String key) {
        String codec = string(format, key, "");
        return !codec.isBlank() && !codec.equalsIgnoreCase("none");
    }

    private static long urlExpiry(String url) {
        try {
            URI uri = URI.create(url);
            String expire = queryValue(uri.getRawQuery(), "expire");
            if (expire == null) return -1;
            return Long.parseLong(URLDecoder.decode(expire, StandardCharsets.UTF_8)) * 1000L;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static List<SubtitleCandidate> subtitleCandidates(JsonObject metadata, String referer) {
        if (metadata == null) return List.of();
        ArrayList<SubtitleCandidate> result = new ArrayList<>();
        addSubtitleCandidates(result, object(metadata, "subtitles"), referer, false);
        addSubtitleCandidates(result, object(metadata, "automatic_captions"), referer, true);
        result.sort(Comparator.comparingInt(SubtitleCandidate::score).reversed());
        if (result.size() > MAX_SUBTITLE_OPTIONS) {
            return List.copyOf(result.subList(0, MAX_SUBTITLE_OPTIONS));
        }
        return List.copyOf(result);
    }

    private static void addSubtitleCandidates(List<SubtitleCandidate> result, JsonObject subtitles, String referer, boolean automatic) {
        if (subtitles == null || subtitles.isEmpty()) return;
        int index = result.size();
        for (String language : subtitles.keySet()) {
            if (language != null && language.equalsIgnoreCase("live_chat")) continue;
            JsonElement element = subtitles.get(language);
            if (element == null || !element.isJsonArray()) continue;
            JsonObject format = bestSubtitleFormat(element.getAsJsonArray());
            if (format == null) continue;
            if (string(format, "protocol", "").equalsIgnoreCase("youtube_live_chat")) continue;
            String url = string(format, "url", "");
            if (url.isBlank()) continue;
            String name = string(format, "name", "");
            String label = subtitleLabel(language, name, automatic);
            String key = "yt:" + (automatic ? "auto:" : "sub:") + (language == null || language.isBlank() ? "unknown" : language) + ":" + index;
            result.add(new SubtitleCandidate(key, label, language, url, automatic, subtitleScore(language, label, automatic, index++)));
        }
    }

    private static JsonObject bestSubtitleFormat(JsonArray formats) {
        if (formats == null || formats.isEmpty()) return null;
        JsonObject fallback = null;
        for (JsonElement element : formats) {
            if (!element.isJsonObject()) continue;
            JsonObject format = element.getAsJsonObject();
            String url = string(format, "url", "");
            if (url.isBlank()) continue;
            if (fallback == null) fallback = format;
            String ext = string(format, "ext", "").toLowerCase(Locale.ROOT);
            if (ext.equals("vtt") || url.contains("fmt=vtt")) return format;
        }
        return fallback;
    }

    private static String subtitleLabel(String language, String name, boolean automatic) {
        String label = name == null || name.isBlank() ? language : name;
        if (label == null || label.isBlank()) label = "CC";
        return automatic ? label + " (auto)" : label;
    }

    private static int subtitleScore(String language, String label, boolean automatic, int index) {
        String lan = language == null ? "" : language.toLowerCase(Locale.ROOT);
        String text = label == null ? "" : label;
        int score = Math.max(0, 100 - index);
        if (!automatic) score += 10_000;
        if (lan.equals("zh-hans") || lan.equals("zh-cn")) score += 1000;
        else if (lan.startsWith("zh")) score += 900;
        else if (text.contains("中文") || text.contains("汉语") || text.contains("漢語")) score += 800;
        else if (lan.equals("en-us") || lan.startsWith("en")) score += 200;
        return score;
    }

    private YtDlpResult runYtDlp(String url, int heightLimit, ResolutionCancellation cancellation,
                                 long resolutionId, String threadName) throws Exception {
        try {
            return runYtDlpOnce(url, heightLimit, cancellation, resolutionId, threadName, configuredYtdlPath);
        } catch (YtDlpMissingException missing) {
            cancellation.throwIfCancelled();
            String installed = installMissingYtdl();
            if (installed.isBlank()) throw missing;
            configuredYtdlPath = installed;
            LOGGER.info("Retrying YouTube resolution #{} with the installed yt-dlp executable", resolutionId);
            return runYtDlpOnce(url, heightLimit, cancellation, resolutionId, threadName, installed);
        }
    }

    private YtDlpResult runYtDlpOnce(String url, int heightLimit, ResolutionCancellation cancellation,
                                     long resolutionId, String threadName, String preferredExecutable) throws Exception {
        YtDlpStartException missing = null;
        cancellation.throwIfCancelled();
        String configured = VideoParams.normalizeMpvOptionValue(preferredExecutable);
        if (!configured.isBlank()) {
            try {
                return executeYtDlp(configured, url, heightLimit, cancellation, resolutionId, threadName);
            } catch (YtDlpStartException e) {
                LOGGER.warn("Configured yt-dlp executable could not be started", e);
                missing = e;
            }
        }
        for (String executable : YT_DLP_CANDIDATES) {
            cancellation.throwIfCancelled();
            try {
                return executeYtDlp(executable, url, heightLimit, cancellation, resolutionId, threadName);
            } catch (YtDlpStartException e) {
                missing = e;
            }
        }
        throw new YtDlpMissingException(missing);
    }

    private static String installMissingYtdl() {
        MissingYtDlpHandler handler = missingYtDlpHandler;
        if (handler == null) return "";
        try {
            String executable = handler.ensure();
            return VideoParams.normalizeMpvOptionValue(executable);
        } catch (Throwable error) {
            LOGGER.warn("Automatic yt-dlp installation callback failed: {}", failureSummary(error));
            return "";
        }
    }

    private YtDlpResult executeYtDlp(String executable, String url, int heightLimit,
                                     ResolutionCancellation cancellation, long resolutionId,
                                     String threadName) throws Exception {
        ArrayList<String> command = new ArrayList<>();
        command.add(executable);
        command.add("--dump-single-json");
        command.add("--no-playlist");
        command.add("--no-warnings");
        command.add("--skip-download");
        command.add("--format");
        command.add(formatSelector(heightLimit));
        appendCookieArguments(command);
        String proxy = VideoParams.normalizeHttpProxy(configuredProxy);
        String commandProxy = proxyWithoutCredentials(proxy);
        if (!commandProxy.isBlank()) {
            command.add("--proxy");
            command.add(commandProxy);
        }
        command.add(url);

        cancellation.throwIfCancelled();
        long startedAt = System.nanoTime();
        LOGGER.info("Starting yt-dlp resolution #{} executable={} url={} format={} proxy={} cookies={}",
                resolutionId, executableName(executable), VideoProviders.redactedSource(url), formatSelector(heightLimit),
                commandProxy.isBlank() ? "none" : VideoProviders.redactedSource(commandProxy), cookieMode());
        Process process;
        try {
            process = processStarter.start(List.copyOf(command), proxyEnvironment(proxy));
        } catch (IOException e) {
            cancellation.throwIfCancelled();
            throw new YtDlpStartException(e);
        }
        LOGGER.info("yt-dlp resolution #{} process started pid={}", resolutionId, process.pid());
        RunningProcess running = new RunningProcess(process, threadName);
        cancellation.attach(running);
        try {
            cancellation.throwIfCancelled();
            boolean completed;
            try {
                completed = process.waitFor(YT_DLP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                running.cancel();
                Thread.currentThread().interrupt();
                throw cancelled(e);
            }
            if (!completed) {
                running.cancel();
                try {
                    process.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw cancelled(e);
                }
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                LOGGER.warn("yt-dlp resolution #{} timed out after {} ms", resolutionId, elapsedMs);
                throw new IOException("yt-dlp timed out after " + elapsedMs + " ms");
            }
            cancellation.throwIfCancelled();
            YtDlpResult result = new YtDlpResult(process.exitValue(), running.stdout(), running.stderr());
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            LOGGER.info("yt-dlp resolution #{} finished exit={} stdoutBytes={} stderrBytes={} elapsedMs={}",
                    resolutionId, result.exitCode, utf8Bytes(result.stdout), utf8Bytes(result.stderr), elapsedMs);
            if (!result.stderr.isBlank()) {
                LOGGER.warn("yt-dlp resolution #{} stderr: {}", resolutionId, summarizeOutput(result.stderr));
            }
            return result;
        } finally {
            cancellation.detach(running);
            running.closeReaders();
        }
    }

    static String formatSelector(int heightLimit) {
        int normalized = YouTubeQuality.normalizeScreenLimit(heightLimit);
        String height = normalized == YouTubeQuality.AUTO ? "" : "[height<=" + normalized + "]";
        return "bestvideo" + height + "[vcodec^=avc1]+bestaudio[acodec^=mp4a]"
                + "/best" + height + "[vcodec^=avc1][acodec^=mp4a]"
                + "/bestvideo" + height + "[vcodec^=avc1]+bestaudio"
                + "/best" + height + "[vcodec^=avc1]"
                + "/bestvideo" + height + "+bestaudio[acodec^=mp4a]"
                + "/best" + height + "[acodec^=mp4a]"
                + "/bestvideo" + height + "+bestaudio"
                + "/best" + height;
    }

    private static StreamReader readAsync(InputStream input, String threadName) {
        CompletableFuture<String> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try (InputStream in = input) {
                result.complete(new String(readLimited(in, MAX_YTDLP_OUTPUT_BYTES), StandardCharsets.UTF_8));
            } catch (IOException e) {
                result.completeExceptionally(e);
            }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
        return new StreamReader(input, thread, result);
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            if (total > maxBytes - read) {
                throw new IOException("yt-dlp output exceeds " + maxBytes + " bytes");
            }
            output.write(buffer, 0, read);
            total += read;
        }
        return output.toByteArray();
    }

    public static List<Integer> availableQualities(String rawPath) {
        String key = normalizedUrl(rawPath);
        if (key == null) return List.of();
        List<Integer> qualities = AVAILABLE_QUALITIES.getIfPresent(key);
        return qualities == null ? List.of() : qualities;
    }

    public static boolean isYouTubeRawPath(String rawPath) {
        return normalizedUrl(rawPath) != null;
    }

    static void updateAvailableQualities(String rawPath, JsonObject metadata) {
        String key = normalizedUrl(rawPath);
        if (key == null) return;
        List<Integer> detected = detectedQualities(metadata);
        if (detected.isEmpty()) return;
        Set<Integer> combined = new HashSet<>(detected);
        List<Integer> existing = AVAILABLE_QUALITIES.getIfPresent(key);
        if (existing != null) combined.addAll(existing);
        AVAILABLE_QUALITIES.put(key, YouTubeQuality.filterSupported(combined));
    }

    static List<Integer> detectedQualities(JsonObject metadata) {
        if (metadata == null) return List.of();
        Set<Integer> heights = new HashSet<>();
        addFormatHeights(heights, array(metadata, "formats"));
        addFormatHeights(heights, array(metadata, "requested_formats"));
        addFormatHeight(heights, metadata);
        return YouTubeQuality.filterSupported(heights);
    }

    private static void addFormatHeights(Set<Integer> heights, JsonArray formats) {
        if (formats == null) return;
        for (JsonElement element : formats) {
            if (element.isJsonObject()) addFormatHeight(heights, element.getAsJsonObject());
        }
    }

    private static void addFormatHeight(Set<Integer> heights, JsonObject format) {
        if (format == null || !hasCodec(format, "vcodec")) return;
        JsonElement value = format.get("height");
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return;
        try {
            int height = value.getAsInt();
            if (height > 0) heights.add(height);
        } catch (RuntimeException ignored) {
        }
    }

    private static String normalizedUrl(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isBlank()) return null;
        String withScheme = hasScheme(value) ? value : "https://" + value;
        URI uri;
        try {
            uri = URI.create(withScheme);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) return null;
        String host = uri.getHost();
        if (!isYouTubeHost(host)) return null;
        if (!hasYouTubeVideoId(uri)) return null;
        String id = idFromUrl(withScheme);
        return id == null || id.isBlank() ? null : "https://www.youtube.com/watch?v=" + id;
    }

    private static boolean hasScheme(String value) {
        int separator = value.indexOf("://");
        if (separator <= 0) return false;
        for (int i = 0; i < separator; i++) {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '+' && c != '-' && c != '.') return false;
        }
        return true;
    }

    private static boolean isYouTubeHost(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("youtu.be")
                || normalized.equals("youtube.com")
                || normalized.endsWith(".youtube.com");
    }

    private static boolean hasYouTubeVideoId(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (host.equals("youtu.be")) {
            return firstPathSegment(path) != null;
        }
        if (path.equals("/watch")) {
            return queryValue(uri.getRawQuery(), "v") != null;
        }
        return path.startsWith("/shorts/")
                || path.startsWith("/live/")
                || path.startsWith("/embed/")
                || path.startsWith("/v/");
    }

    private static String idFromUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (host.equals("youtu.be")) return firstPathSegment(path);
            if (path.equals("/watch")) return queryValue(uri.getRawQuery(), "v");
            for (String prefix : List.of("/shorts/", "/live/", "/embed/", "/v/")) {
                if (path.startsWith(prefix)) return firstPathSegment(path.substring(prefix.length() - 1));
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    private static String firstPathSegment(String path) {
        if (path == null) return null;
        String trimmed = path;
        while (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        if (trimmed.isBlank()) return null;
        int slash = trimmed.indexOf('/');
        return slash < 0 ? trimmed : trimmed.substring(0, slash);
    }

    private static String queryValue(String query, String key) {
        if (query == null || query.isBlank()) return null;
        for (String part : query.split("&")) {
            int equals = part.indexOf('=');
            String name = equals < 0 ? part : part.substring(0, equals);
            if (name.equals(key)) {
                String value = equals < 0 ? "" : part.substring(equals + 1);
                return value.isBlank() ? null : value;
            }
        }
        return null;
    }

    private static long durationMs(JsonObject metadata) {
        if (metadata == null) return 0L;
        JsonElement duration = metadata.get("duration");
        if (duration == null || duration.isJsonNull() || !duration.isJsonPrimitive()) return 0L;
        try {
            double seconds = duration.getAsDouble();
            return seconds > 0 ? Math.round(seconds * Duration.ofSeconds(1).toMillis()) : 0L;
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key)) return fallback;
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        String value = element.getAsString();
        return value == null || value.isBlank() ? fallback : value;
    }

    private static JsonObject object(JsonObject object, String key) {
        if (object == null || !object.has(key)) return null;
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() || !element.isJsonObject() ? null : element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String key) {
        if (object == null || !object.has(key)) return null;
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() || !element.isJsonArray() ? null : element.getAsJsonArray();
    }

    private static CancellationException cancelled(Throwable cause) {
        CancellationException exception = new CancellationException("yt-dlp resolution cancelled");
        if (cause != null) exception.initCause(cause);
        return exception;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private static int utf8Bytes(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String summarizeOutput(String value) {
        if (value == null || value.isBlank()) return "<empty>";
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        normalized = redactSensitive(normalized);
        int max = 1024;
        if (normalized.length() <= max) return normalized;
        return normalized.substring(0, max) + "...";
    }

    private static String safeSetting(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim().replace("\r", "").replace("\n", "").replace("\u0000", "");
        return normalized.length() > 4096 ? normalized.substring(0, 4096) : normalized;
    }

    private static void appendCookieArguments(List<String> command) {
        String file = safeSetting(configuredCookiesFile);
        if (!file.isBlank()) {
            command.add("--cookies");
            command.add(file);
            return;
        }
        String browser = safeSetting(configuredCookiesFromBrowser);
        if (!browser.isBlank()) {
            command.add("--cookies-from-browser");
            command.add(browser);
        }
    }

    private static String cookieMode() {
        if (!safeSetting(configuredCookiesFile).isBlank()) return "file";
        if (!safeSetting(configuredCookiesFromBrowser).isBlank()) return "browser";
        return "none";
    }

    private static String executableName(String executable) {
        if (executable == null || executable.isBlank()) return "<path>";
        try {
            java.nio.file.Path path = java.nio.file.Path.of(executable);
            java.nio.file.Path name = path.getFileName();
            return name == null || name.toString().isBlank() ? "<path>" : name.toString();
        } catch (RuntimeException ignored) {
            return "<path>";
        }
    }

    private static String redactSensitive(String value) {
        String result = value == null ? "" : value;
        String cookiesFile = safeSetting(configuredCookiesFile);
        String browser = safeSetting(configuredCookiesFromBrowser);
        if (!cookiesFile.isBlank()) result = result.replace(cookiesFile, "<redacted-cookie-file>");
        if (!browser.isBlank()) result = result.replace(browser, "<redacted-browser-profile>");
        result = result.replaceAll("(?i)(cookie|authorization|token|password|passwd|secret|session[_-]?id|sid)\\s*[=:]\\s*[^\\s,;]+", "$1=<redacted>");
        result = result.replaceAll("(?i)([?&](?:token|sig|signature|expire|expires|key|sparams|lsig|sp|si)=)[^&#\\s]+", "$1<redacted>");
        return result;
    }

    private static String failureSummary(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return summarizeOutput(message == null ? error.getClass().getSimpleName() : message);
    }

    private static String classifyFailure(String stderr) {
        String value = stderr == null ? "" : stderr.toLowerCase(Locale.ROOT);
        if (value.contains("sign in") || value.contains("login") || value.contains("cookies")
                || value.contains("confirm you're not a bot") || value.contains("confirm you are not a bot")
                || value.contains("age-restricted") || value.contains("age restricted")) {
            return "login_required";
        }
        if (value.contains("private video") || value.contains("this video is private")) return "private";
        if (value.contains("geo-restricted") || value.contains("not available in your country")
                || value.contains("country restriction")) return "geo_restricted";
        if (value.contains("video unavailable") || value.contains("requested format is not available")) {
            return "video_unavailable";
        }
        if (value.contains("timed out") || value.contains("network") || value.contains("connection")) {
            return "network";
        }
        return "unknown";
    }

    private static VpTranslation failureTranslation(String classification) {
        return switch (classification) {
            case "login_required" -> VpTranslation.of("message.videoplayer.youtube_login_required",
                    "YouTube requires login or valid cookies. Configure a cookies.txt file or browser profile.");
            case "private" -> VpTranslation.of("message.videoplayer.youtube_private",
                    "This YouTube video is private. Sign in with an account that can access it.");
            case "geo_restricted" -> VpTranslation.of("message.videoplayer.youtube_geo_restricted",
                    "This YouTube video is unavailable in the configured network region.");
            case "video_unavailable" -> VpTranslation.of("message.videoplayer.youtube_video_unavailable",
                    "YouTube did not provide a playable format for this video.");
            case "network" -> VpTranslation.of("message.videoplayer.youtube_network_failed",
                    "yt-dlp could not reach YouTube. Check the proxy and network connection.");
            default -> VpTranslation.of("message.videoplayer.youtube_metadata_failed",
                    "Failed to resolve YouTube metadata. Update yt-dlp and verify the URL, cookies, and proxy.");
        };
    }

    private static void replyFailure(IProviderSource source, String classification) {
        if (source == null) return;
        source.reply(failureTranslation(classification));
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(List<String> command) throws IOException;

        default Process start(List<String> command, Map<String, String> environment) throws IOException {
            return start(command);
        }
    }

    @FunctionalInterface
    public interface MissingYtDlpHandler {
        String ensure();
    }

    private static final class DefaultProcessStarter implements ProcessStarter {
        @Override
        public Process start(List<String> command) throws IOException {
            return new ProcessBuilder(command).start();
        }

        @Override
        public Process start(List<String> command, Map<String, String> environment) throws IOException {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (environment != null && !environment.isEmpty()) builder.environment().putAll(environment);
            return builder.start();
        }
    }

    private static String proxyWithoutCredentials(String proxy) {
        if (proxy == null || proxy.isBlank()) return "";
        try {
            URI uri = URI.create(proxy);
            if (uri.getUserInfo() == null) return proxy;
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static Map<String, String> proxyEnvironment(String proxy) {
        if (proxy == null || proxy.isBlank()) return Map.of();
        HashMap<String, String> environment = new HashMap<>();
        environment.put("HTTP_PROXY", proxy);
        environment.put("HTTPS_PROXY", proxy);
        environment.put("http_proxy", proxy);
        environment.put("https_proxy", proxy);
        return Map.copyOf(environment);
    }

    private static final class ResolutionFuture extends CompletableFuture<VideoInfo> {
        private final ResolutionCancellation cancellation;
        private volatile Thread worker;

        ResolutionFuture(ResolutionCancellation cancellation) {
            this.cancellation = cancellation;
        }

        void setWorker(Thread worker) {
            this.worker = worker;
            if (isCancelled()) worker.interrupt();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                cancellation.cancel();
                Thread currentWorker = worker;
                if (currentWorker != null) currentWorker.interrupt();
            }
            return cancelled;
        }
    }

    private static final class ResolutionCancellation {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicReference<RunningProcess> running = new AtomicReference<>();

        void attach(RunningProcess process) {
            if (!running.compareAndSet(null, process)) {
                process.cancel();
                throw new IllegalStateException("yt-dlp process already attached");
            }
            throwIfCancelled();
        }

        void detach(RunningProcess process) {
            running.compareAndSet(process, null);
        }

        void cancel() {
            cancelled.set(true);
            RunningProcess process = running.get();
            if (process != null) process.cancel();
        }

        void throwIfCancelled() {
            if (!cancelled.get() && !Thread.currentThread().isInterrupted()) return;
            cancel();
            throw cancelled(null);
        }
    }

    private static final class RunningProcess {
        private final Process process;
        private final StreamReader stdout;
        private final StreamReader stderr;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        RunningProcess(Process process, String threadName) {
            this.process = process;
            stdout = readAsync(process.getInputStream(), threadName + "-stdout");
            stderr = readAsync(process.getErrorStream(), threadName + "-stderr");
        }

        String stdout() throws ExecutionException, TimeoutException {
            return stdout.await();
        }

        String stderr() throws ExecutionException, TimeoutException {
            return stderr.await();
        }

        void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            try {
                process.destroyForcibly();
            } catch (RuntimeException ignored) {
            }
            closeQuietly(process.getOutputStream());
            closeReaders();
        }

        void closeReaders() {
            stdout.cancel();
            stderr.cancel();
        }
    }

    private static final class StreamReader {
        private final InputStream input;
        private final Thread thread;
        private final CompletableFuture<String> result;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        StreamReader(InputStream input, Thread thread, CompletableFuture<String> result) {
            this.input = input;
            this.thread = thread;
            this.result = result;
        }

        String await() throws ExecutionException, TimeoutException {
            try {
                return result.get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw cancelled(e);
            }
        }

        void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            closeQuietly(input);
            thread.interrupt();
            result.cancel(true);
        }
    }

    private record YtDlpResult(int exitCode, String stdout, String stderr) {
    }

    private record SubtitleCandidate(String key, String label, String language, String url, boolean automatic, int score) {
    }

    private record ResolvedMedia(String videoUrl, String audioUrl, boolean hasAudio, JsonObject headers, long expire) {
    }

    private static final class YtDlpMissingException extends Exception {
        YtDlpMissingException(Throwable cause) {
            super(cause);
        }
    }

    private static final class YtDlpStartException extends IOException {
        YtDlpStartException(Throwable cause) {
            super(cause);
        }
    }
}
