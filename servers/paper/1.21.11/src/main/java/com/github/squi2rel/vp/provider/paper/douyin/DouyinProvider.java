package com.github.squi2rel.vp.provider.paper.douyin;

import com.github.squi2rel.vp.HttpProxyConfig;
import com.github.squi2rel.vp.i18n.VpTranslation;
import com.github.squi2rel.vp.provider.IProviderSource;
import com.github.squi2rel.vp.provider.IVideoProvider;
import com.github.squi2rel.vp.provider.VideoInfo;
import com.github.squi2rel.vp.provider.paper.AsyncVideoProvider;
import com.github.squi2rel.vp.provider.paper.ProviderAsyncExecutor;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class DouyinProvider extends AsyncVideoProvider implements IVideoProvider {
    private static final long VIDEO_TTL_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final long LIVE_TTL_MILLIS = Duration.ofSeconds(10).toMillis();
    private static final long MAX_H264_PIXELS = 1920L * 1080L;
    private final DouyinHttpClient http;
    private final DouyinUrlResolver urls;
    private final LongSupplier currentTimeMillis;
    private final Cache<String, ResolvedVideo> videos = CacheBuilder.newBuilder()
            .maximumSize(512)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();
    private final Cache<String, ResolvedLive> lives = CacheBuilder.newBuilder()
            .maximumSize(256)
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .build();

    public DouyinProvider(ProviderAsyncExecutor executor, HttpProxyConfig proxy) {
        this(executor, new DouyinHttpClient(proxy), System::currentTimeMillis);
    }

    DouyinProvider(ProviderAsyncExecutor executor, DouyinHttpClient http, LongSupplier currentTimeMillis) {
        super(executor, 8);
        this.http = http;
        this.urls = new DouyinUrlResolver(http);
        this.currentTimeMillis = currentTimeMillis;
    }

    @Override
    public @Nullable CompletableFuture<VideoInfo> from(String str, IProviderSource source) {
        URI submitted = urls.submitted(str);
        if (submitted == null) return null;
        return submit(() -> resolve(submitted, source));
    }

    private VideoInfo resolve(URI submitted, IProviderSource source) throws Exception {
        DouyinUrlResolver.Target target = urls.resolve(submitted);
        return switch (target.kind()) {
            case VIDEO -> video(target, source);
            case LIVE -> live(target, source);
        };
    }

    private VideoInfo video(DouyinUrlResolver.Target target, IProviderSource source) throws Exception {
        long now = currentTimeMillis.getAsLong();
        ResolvedVideo resolved = videos.getIfPresent(target.id());
        if (resolved == null || now >= resolved.expiresAt()) {
            JsonObject detail = http.videoDetail(target.id());
            resolved = parseVideo(detail, target.id(), target.canonicalUrl(), http::playableVideo, now);
            videos.put(target.id(), resolved);
        }
        return new VideoInfo(
                source.name(), resolved.title(), resolved.path(), target.canonicalUrl(), resolved.expiresAt(),
                true, playbackParams(target.canonicalUrl()), resolved.durationMs()
        );
    }

    private VideoInfo live(DouyinUrlResolver.Target target, IProviderSource source) throws Exception {
        long now = currentTimeMillis.getAsLong();
        ResolvedLive resolved = lives.getIfPresent(target.id());
        if (resolved == null) {
            DouyinLivePageParser.LiveInfo info = DouyinLivePageParser.parse(http.livePage(target.id()));
            if (info == null) throw new IllegalStateException("Douyin live page did not contain room state");
            if (info.status() != 2) {
                source.reply(VpTranslation.of(
                        "message.videoplayer.douyin_live_offline",
                        "The Douyin live room is not streaming"
                ));
                return null;
            }
            String stream = info.hls().isBlank() ? info.flv() : info.hls();
            URI media = http.liveMedia(stream);
            if (media == null) throw new IllegalStateException("Douyin live stream address is unavailable");
            String title = info.title().isBlank() ? "Douyin Live " + target.id() : info.title();
            resolved = new ResolvedLive(title, media.toString());
            lives.put(target.id(), resolved);
        }
        return new VideoInfo(
                source.name(), resolved.title(), resolved.path(), target.canonicalUrl(), now + LIVE_TTL_MILLIS,
                false, playbackParams(target.canonicalUrl()), 0L
        );
    }

    @Override
    public void close() {
        super.close();
        videos.invalidateAll();
        lives.invalidateAll();
        http.close();
    }

    static ResolvedVideo parseVideo(JsonObject detail, String id, String canonicalUrl,
                                     MediaResolver mediaResolver, long now) throws Exception {
        JsonObject video = object(detail, "video");
        if (video == null) throw new IllegalStateException("Douyin video metadata is missing");
        List<MediaCandidate> candidates = orderedCandidates(video);
        URI selected = null;
        for (MediaCandidate candidate : candidates) {
            selected = mediaResolver.resolve(candidate.urls(), canonicalUrl);
            if (selected != null) break;
        }
        if (selected == null) throw new IllegalStateException("Douyin video did not provide a playable stream");
        String title = string(detail, "desc");
        if (title.isBlank()) title = "Douyin " + id;
        long duration = Math.max(0L, longValue(video, "duration", 0L));
        return new ResolvedVideo(title, selected.toString(), now + VIDEO_TTL_MILLIS, duration);
    }

    static List<MediaCandidate> orderedCandidates(JsonObject video) {
        ArrayList<MediaCandidate> h264 = new ArrayList<>();
        ArrayList<MediaCandidate> fallback = new ArrayList<>();
        JsonArray rates = array(video, "bit_rate");
        if (rates != null) {
            for (JsonElement element : rates) {
                if (!element.isJsonObject()) continue;
                JsonObject rate = element.getAsJsonObject();
                JsonObject address = object(rate, "play_addr");
                if (address == null) continue;
                boolean h266 = integer(rate, "is_bytevc2", 0) == 1
                        || string(rate, "gear_name").toLowerCase(java.util.Locale.ROOT).contains("bytevc2");
                if (h266) continue;
                boolean compatible = integer(rate, "is_h265", 0) != 1 && integer(rate, "is_bytevc1", 0) != 1;
                MediaCandidate candidate = candidate(address, compatible,
                        integer(rate, "FPS", integer(rate, "fps", 0)), longValue(rate, "bit_rate", 0L));
                if (candidate.urls().isEmpty()) continue;
                (compatible ? h264 : fallback).add(candidate);
            }
        }
        ArrayList<MediaCandidate> ordered = new ArrayList<>();
        ordered.addAll(ranked(h264));
        addAddress(ordered, object(video, "play_addr_h264"), true);
        ordered.addAll(ranked(fallback));
        addAddress(ordered, object(video, "play_addr_265"), false);
        addAddress(ordered, object(video, "play_addr"), false);
        return deduplicate(ordered);
    }

    private static List<MediaCandidate> ranked(List<MediaCandidate> candidates) {
        ArrayList<MediaCandidate> within = new ArrayList<>();
        ArrayList<MediaCandidate> above = new ArrayList<>();
        for (MediaCandidate candidate : candidates) {
            if (candidate.pixels() <= MAX_H264_PIXELS && Math.max(candidate.width(), candidate.height()) <= 1920) {
                within.add(candidate);
            } else {
                above.add(candidate);
            }
        }
        Comparator<MediaCandidate> descending = Comparator.comparingLong(MediaCandidate::pixels)
                .thenComparingInt(MediaCandidate::fps)
                .thenComparingLong(MediaCandidate::bitrate)
                .reversed();
        within.sort(descending);
        above.sort(Comparator.comparingLong((MediaCandidate candidate) -> Math.abs(candidate.pixels() - MAX_H264_PIXELS))
                .thenComparing(Comparator.comparingInt(MediaCandidate::fps).reversed())
                .thenComparing(Comparator.comparingLong(MediaCandidate::bitrate).reversed()));
        ArrayList<MediaCandidate> ordered = new ArrayList<>(within.size() + above.size());
        ordered.addAll(within);
        ordered.addAll(above);
        return ordered;
    }

    private static MediaCandidate candidate(JsonObject address, boolean h264, int fps, long bitrate) {
        int width = integer(address, "width", 0);
        int height = integer(address, "height", 0);
        return new MediaCandidate(width, height, fps, bitrate, h264, urls(address));
    }

    private static void addAddress(List<MediaCandidate> output, JsonObject address, boolean h264) {
        if (address == null) return;
        MediaCandidate candidate = candidate(address, h264, 0, 0L);
        if (!candidate.urls().isEmpty()) output.add(candidate);
    }

    private static List<MediaCandidate> deduplicate(List<MediaCandidate> candidates) {
        LinkedHashSet<List<String>> seen = new LinkedHashSet<>();
        ArrayList<MediaCandidate> output = new ArrayList<>();
        for (MediaCandidate candidate : candidates) {
            if (seen.add(candidate.urls())) output.add(candidate);
        }
        return output;
    }

    private static List<String> urls(JsonObject address) {
        JsonArray values = array(address, "url_list");
        if (values == null) return List.of();
        ArrayList<String> urls = new ArrayList<>();
        for (JsonElement value : values) {
            if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) {
                String url = value.getAsString();
                if (!url.isBlank()) urls.add(url);
            }
        }
        return List.copyOf(urls);
    }

    private static String[] playbackParams(String referer) {
        return new String[]{"user-agent=" + DouyinHttpClient.USER_AGENT, "referrer=" + referer};
    }

    private static JsonObject object(JsonObject parent, String name) {
        if (parent == null || !parent.has(name)) return null;
        JsonElement value = parent.get(name);
        return value == null || value.isJsonNull() || !value.isJsonObject() ? null : value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject parent, String name) {
        if (parent == null || !parent.has(name)) return null;
        JsonElement value = parent.get(name);
        return value == null || value.isJsonNull() || !value.isJsonArray() ? null : value.getAsJsonArray();
    }

    private static String string(JsonObject parent, String name) {
        if (parent == null || !parent.has(name)) return "";
        JsonElement value = parent.get(name);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? "" : value.getAsString();
    }

    private static int integer(JsonObject parent, String name, int fallback) {
        if (parent == null || !parent.has(name)) return fallback;
        try {
            return parent.get(name).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject parent, String name, long fallback) {
        if (parent == null || !parent.has(name)) return fallback;
        try {
            return parent.get(name).getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    @FunctionalInterface
    interface MediaResolver {
        URI resolve(List<String> urls, String referer) throws Exception;
    }

    record MediaCandidate(int width, int height, int fps, long bitrate, boolean h264, List<String> urls) {
        long pixels() {
            return Math.max(0L, (long) width * height);
        }
    }

    record ResolvedVideo(String title, String path, long expiresAt, long durationMs) {
    }

    private record ResolvedLive(String title, String path) {
    }
}
