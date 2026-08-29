package com.github.squi2rel.vp.provider.paper;

import com.github.squi2rel.vp.HttpProxyConfig;
import com.github.squi2rel.vp.provider.IProviderSource;
import com.github.squi2rel.vp.provider.VideoInfo;
import com.github.squi2rel.vp.provider.bilibili.BiliBiliVideoProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class B23NetworkSmokeTest {
    private static final ProviderAsyncExecutor DIRECT = runnable -> {
        runnable.run();
        return () -> {
        };
    };

    @Test
    void resolvesConfiguredShortLink() throws Exception {
        String url = System.getenv("VIDEOPLAYER_B23_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank());
        String proxy = System.getenv("VIDEOPLAYER_NETWORK_PROXY");
        try (B23VideoProvider provider = new B23VideoProvider(
                DIRECT,
                HttpProxyConfig.parse(proxy),
                new BiliBiliVideoProvider()
        )) {
            CompletableFuture<VideoInfo> future = provider.from(url, new Source());
            assertNotNull(future);
            VideoInfo info = future.get();
            assertNotNull(info);
            assertFalse(info.path().isBlank());
        }
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
