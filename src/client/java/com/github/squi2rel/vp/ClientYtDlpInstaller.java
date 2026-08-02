package com.github.squi2rel.vp;

import net.minecraft.client.MinecraftClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class ClientYtDlpInstaller {
    public enum State {
        IDLE,
        CHECKING,
        INSTALLING,
        AVAILABLE,
        FAILED
    }

    private static final AtomicReference<CompletableFuture<YtDlpManager.EnsureResult>> IN_FLIGHT = new AtomicReference<>();
    private static volatile State state = State.IDLE;
    private static volatile YtDlpManager.EnsureResult lastResult;

    private ClientYtDlpInstaller() {
    }

    public static CompletableFuture<YtDlpManager.EnsureResult> ensureAsync() {
        return ensureStarted();
    }

    public static YtDlpManager.EnsureResult ensureBlocking() {
        return ensureStarted().join();
    }

    private static CompletableFuture<YtDlpManager.EnsureResult> ensureStarted() {
        while (true) {
            CompletableFuture<YtDlpManager.EnsureResult> current = IN_FLIGHT.get();
            if (current != null) return current;
            CompletableFuture<YtDlpManager.EnsureResult> created = new CompletableFuture<>();
            if (!IN_FLIGHT.compareAndSet(null, created)) continue;
            state = State.CHECKING;
            CompletableFuture.supplyAsync(ClientYtDlpInstaller::install)
                    .whenComplete((result, error) -> {
                        if (error != null) created.completeExceptionally(error);
                        else created.complete(result);
                    });
            created.whenComplete((result, error) -> {
                IN_FLIGHT.compareAndSet(created, null);
                if (error != null) {
                    state = State.FAILED;
                    VideoPlayerMain.LOGGER.warn("Automatic yt-dlp installation failed", error);
                    return;
                }
                publish(result);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) client.execute(VideoPlayerClient::applyNativePlatformConfig);
            });
            return created;
        }
    }

    private static YtDlpManager.EnsureResult install() {
        Config config = VideoPlayerClient.config;
        String configured = config == null ? "" : config.mpvYtdlPath;
        String proxy = config == null ? "" : config.nativeDownloadProxy;
        NativeDownloadConfig downloads = VideoPlayerClient.nativeDownloadConfig();
        state = State.INSTALLING;
        YtDlpManager.EnsureResult result = YtDlpManager.ensureAvailable(
                configured,
                downloads,
                NativeDownloadConfig.platformKey(),
                proxy,
                null
        );
        return result;
    }

    private static void publish(YtDlpManager.EnsureResult result) {
        lastResult = result;
        state = result != null && result.available() ? State.AVAILABLE : State.FAILED;
    }

    public static State state() {
        return state;
    }

    public static YtDlpManager.EnsureResult lastResult() {
        return lastResult;
    }
}
