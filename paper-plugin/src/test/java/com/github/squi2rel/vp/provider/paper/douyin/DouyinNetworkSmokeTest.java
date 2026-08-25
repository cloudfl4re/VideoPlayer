package com.github.squi2rel.vp.provider.paper.douyin;

import com.github.squi2rel.vp.HttpProxyConfig;
import com.github.squi2rel.vp.provider.IProviderSource;
import com.github.squi2rel.vp.provider.VideoInfo;
import com.github.squi2rel.vp.provider.paper.ProviderAsyncExecutor;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DouyinNetworkSmokeTest {
    private static final ProviderAsyncExecutor DIRECT = runnable -> {
        runnable.run();
        return () -> {
        };
    };

    @Test
    void resolvesConfiguredPublicVideo() throws Exception {
        String url = System.getenv("VIDEOPLAYER_DOUYIN_VIDEO_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank());
        try (DouyinProvider provider = provider()) {
            CompletableFuture<VideoInfo> future = provider.from(url, new Source());
            assertNotNull(future);
            VideoInfo info = future.get();
            assertNotNull(info);
            assertTrue(info.seekable());
            assertTrue(info.durationMs() > 0L);
            assertFalse(URI.create(info.path()).getHost().equals("www.douyin.com"));
        }
    }

    @Test
    void resolvesConfiguredLiveRoom() throws Exception {
        String url = System.getenv("VIDEOPLAYER_DOUYIN_LIVE_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank());
        try (DouyinProvider provider = provider()) {
            CompletableFuture<VideoInfo> future = provider.from(url, new Source());
            assertNotNull(future);
            VideoInfo info = future.get();
            assertNotNull(info);
            assertFalse(info.seekable());
            assertTrue(info.path().startsWith("https://"));
        }
    }

    private static DouyinProvider provider() {
        String proxy = System.getenv("VIDEOPLAYER_NETWORK_PROXY");
        return new DouyinProvider(DIRECT, HttpProxyConfig.parse(proxy));
    }

    private static final class Source implements IProviderSource {
        @Override
        public String name() {
            return "network-smoke";
        }

        @Override
        public void reply(String text) {
        }
    }
}
