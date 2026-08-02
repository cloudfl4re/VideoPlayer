package com.github.squi2rel.vp.danmaku;

import com.github.squi2rel.vp.provider.VideoInfo;
import com.github.squi2rel.vp.provider.bilibili.BiliBiliLiveProvider;
import com.github.squi2rel.vp.provider.bilibili.BiliBiliVideoProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BiliBiliSourceRegistry {
    private static final Pattern DIRECT_ROOM = Pattern.compile("^(?:https?://live\\.bilibili\\.com/)?(\\d+)(?:[/?#].*)?$");
    static final int MAX_CACHE_ENTRIES = 256;
    static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L;
    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private BiliBiliSourceRegistry() {
    }

    static boolean canResolve(VideoInfo info) {
        String raw = rawPath(info);
        if (raw.isBlank()) return false;
        return BiliBiliVideoProvider.REGEX.matcher(raw).find()
                || BiliBiliLiveProvider.REGEX.matcher(raw).find()
                || DIRECT_ROOM.matcher(raw).matches();
    }

    static CompletableFuture<BiliBiliSourceInfo> resolve(VideoInfo info) {
        return resolve(rawPath(info), BiliBiliSourceRegistry::resolveBlocking, System.currentTimeMillis());
    }

    static CompletableFuture<BiliBiliSourceInfo> resolve(String raw, Resolver resolver, long now) {
        String key = raw == null ? "" : raw.trim();
        if (key.isBlank() || resolver == null) return CompletableFuture.completedFuture(null);
        cleanup(now);
        AtomicBoolean created = new AtomicBoolean();
        CacheEntry entry = CACHE.compute(key, (ignored, existing) -> {
            if (existing != null && !existing.expired(now) && !existing.failed()) return existing;
            created.set(true);
            return new CacheEntry(CompletableFuture.supplyAsync(() -> resolver.resolve(key)), expiresAt(now), now);
        });
        if (created.get()) {
            entry.future().whenComplete((value, error) -> {
                if (error != null || value == null || entry.future().isCancelled()) {
                    CACHE.remove(key, entry);
                }
            });
        }
        cleanup(now);
        return entry.future();
    }

    static void clear() {
        for (CacheEntry entry : CACHE.values()) {
            entry.future().cancel(true);
        }
        CACHE.clear();
    }

    static int cacheSize() {
        CACHE.entrySet().removeIf(entry -> entry.getValue().failed());
        return CACHE.size();
    }

    private static void cleanup(long now) {
        CACHE.entrySet().removeIf(entry -> entry.getValue().expired(now) || entry.getValue().failed());
        int excess = CACHE.size() - MAX_CACHE_ENTRIES;
        if (excess <= 0) return;
        ArrayList<Map.Entry<String, CacheEntry>> entries = new ArrayList<>(CACHE.entrySet());
        entries.sort(Comparator.comparingLong(entry -> entry.getValue().createdAt()));
        for (Map.Entry<String, CacheEntry> entry : entries) {
            if (excess <= 0) return;
            if (CACHE.remove(entry.getKey(), entry.getValue())) excess--;
        }
    }

    private static long expiresAt(long now) {
        return now > Long.MAX_VALUE - CACHE_TTL_MILLIS ? Long.MAX_VALUE : now + CACHE_TTL_MILLIS;
    }

    private static BiliBiliSourceInfo resolveBlocking(String raw) {
        Matcher video = BiliBiliVideoProvider.REGEX.matcher(raw);
        if (video.find()) return resolveVod(video);

        Matcher live = BiliBiliLiveProvider.REGEX.matcher(raw);
        if (live.find()) return resolveLive(live.group());

        Matcher directRoom = DIRECT_ROOM.matcher(raw);
        if (directRoom.matches()) return resolveLive(directRoom.group(1));
        return null;
    }

    private static BiliBiliSourceInfo resolveVod(Matcher matcher) {
        String bvid = matcher.group(1);
        int page = matcher.group(2) == null ? 1 : Math.max(1, Integer.parseInt(matcher.group(2)));
        try {
            String body = BiliHttp.getString(String.format(BiliBiliVideoProvider.FETCH_URL, bvid), "https://www.bilibili.com/video/" + bvid);
            JsonObject data = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("data");
            long aid = data.get("aid").getAsLong();
            long cid = data.get("cid").getAsLong();
            JsonArray pages = data.getAsJsonArray("pages");
            if (pages != null && page <= pages.size()) {
                cid = pages.get(page - 1).getAsJsonObject().get("cid").getAsLong();
            }
            return BiliBiliSourceInfo.vod(bvid, aid, cid, page);
        } catch (Exception e) {
            return null;
        }
    }

    private static BiliBiliSourceInfo resolveLive(String room) {
        try {
            String body = BiliHttp.getString(String.format(BiliBiliLiveProvider.FETCH_URL, room), "https://live.bilibili.com/" + room);
            JsonObject data = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("data");
            return BiliBiliSourceInfo.live(data.get("room_id").getAsLong());
        } catch (Exception e) {
            try {
                return BiliBiliSourceInfo.live(Long.parseLong(room));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private static String rawPath(VideoInfo info) {
        if (info == null || info.rawPath() == null) return "";
        return info.rawPath().trim();
    }

    @FunctionalInterface
    interface Resolver {
        BiliBiliSourceInfo resolve(String raw);
    }

    private record CacheEntry(CompletableFuture<BiliBiliSourceInfo> future, long expiresAt, long createdAt) {
        private boolean expired(long now) {
            return future.isDone() && now >= expiresAt;
        }

        private boolean failed() {
            return future.isCompletedExceptionally() || future.isCancelled()
                    || future.isDone() && future.getNow(null) == null;
        }
    }
}
