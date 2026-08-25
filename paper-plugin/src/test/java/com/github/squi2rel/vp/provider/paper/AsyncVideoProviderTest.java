package com.github.squi2rel.vp.provider.paper;

import com.github.squi2rel.vp.provider.VideoInfo;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncVideoProviderTest {
    @Test
    void closeCancelsPendingTaskAndFuture() {
        AtomicReference<Runnable> task = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        ProviderAsyncExecutor executor = runnable -> {
            task.set(runnable);
            return () -> cancelled.set(true);
        };
        TestProvider provider = new TestProvider(executor);
        CompletableFuture<VideoInfo> future = provider.start(() -> info("https://cdn.example/video.mp4"));
        provider.close();
        assertTrue(future.isCancelled());
        assertTrue(cancelled.get());
        task.get().run();
        assertTrue(future.isCancelled());
    }

    @Test
    void directExecutorCompletesResolution() throws Exception {
        ProviderAsyncExecutor executor = runnable -> {
            runnable.run();
            return () -> {
            };
        };
        TestProvider provider = new TestProvider(executor);
        assertEquals("https://cdn.example/video.mp4", provider.start(() -> info("https://cdn.example/video.mp4")).get().path());
        provider.close();
    }

    private static VideoInfo info(String path) {
        return new VideoInfo("source", "name", path, "raw", -1, true, new String[0]);
    }

    private static final class TestProvider extends AsyncVideoProvider {
        TestProvider(ProviderAsyncExecutor executor) {
            super(executor, 1);
        }

        CompletableFuture<VideoInfo> start(java.util.concurrent.Callable<VideoInfo> resolver) {
            return submit(resolver);
        }
    }
}
