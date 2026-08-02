package com.github.squi2rel.vp.danmaku;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiliBiliSourceRegistryTest {
    @AfterEach
    void clearCache() {
        BiliBiliSourceRegistry.clear();
    }

    @Test
    void reusesSuccessfulEntriesUntilTheirTtlExpires() {
        AtomicInteger resolutions = new AtomicInteger();
        BiliBiliSourceRegistry.Resolver resolver = raw -> {
            resolutions.incrementAndGet();
            return BiliBiliSourceInfo.live(1L);
        };

        CompletableFuture<BiliBiliSourceInfo> first = BiliBiliSourceRegistry.resolve("room", resolver, 1_000L);
        CompletableFuture<BiliBiliSourceInfo> cached = BiliBiliSourceRegistry.resolve("room", resolver, 1_001L);
        assertEquals(BiliBiliSourceInfo.live(1L), first.join());
        CompletableFuture<BiliBiliSourceInfo> refreshed = BiliBiliSourceRegistry.resolve(
                "room", resolver, 1_000L + BiliBiliSourceRegistry.CACHE_TTL_MILLIS
        );

        assertSame(first, cached);
        assertEquals(BiliBiliSourceInfo.live(1L), refreshed.join());
        assertEquals(2, resolutions.get());
    }

    @Test
    void dropsFailedEntriesImmediately() {
        CompletableFuture<BiliBiliSourceInfo> failed = BiliBiliSourceRegistry.resolve("room", raw -> null, 1_000L);

        assertNull(failed.join());
        assertEquals(0, BiliBiliSourceRegistry.cacheSize());
    }

    @Test
    void boundsTheCacheWhenManyDistinctSourcesAreResolved() {
        for (int index = 0; index < BiliBiliSourceRegistry.MAX_CACHE_ENTRIES + 32; index++) {
            long roomId = index + 1L;
            BiliBiliSourceRegistry.resolve("room-" + roomId, raw -> BiliBiliSourceInfo.live(roomId), index).join();
        }

        assertTrue(BiliBiliSourceRegistry.cacheSize() <= BiliBiliSourceRegistry.MAX_CACHE_ENTRIES);
    }
}
