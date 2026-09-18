package com.github.squi2rel.vp.provider.paper;

import com.github.squi2rel.vp.provider.IProviderSource;
import com.github.squi2rel.vp.provider.VideoInfo;
import com.github.squi2rel.vp.provider.bilibili.BiliBiliVideoProvider;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class B23VideoProviderTest {
    private static final ProviderAsyncExecutor DIRECT = runnable -> {
        runnable.run();
        return () -> {
        };
    };

    @Test
    void expandsAndDelegatesBilibiliVideo() throws Exception {
        AtomicInteger redirects = new AtomicInteger();
        B23VideoProvider provider = new B23VideoProvider(
                DIRECT,
                uri -> {
                    redirects.incrementAndGet();
                    return URI.create("https://www.bilibili.com/video/BV1p98q6dErq/?p=1");
                },
                delegate("https://cdn.example/video.m4s")
        );
        TestSource source = new TestSource();
        VideoInfo first = provider.from("https://b23.tv/KyRzHQT", source).get();
        VideoInfo second = provider.from("视频 https://b23.tv/KyRzHQT 复制", source).get();
        assertEquals("https://cdn.example/video.m4s", first.path());
        assertEquals("https://cdn.example/video.m4s", second.path());
        assertEquals(1, redirects.get());
        provider.close();
    }

    @Test
    void rejectsUnsupportedTargetAndLookalikeHost() throws Exception {
        B23VideoProvider provider = new B23VideoProvider(
                DIRECT,
                uri -> URI.create("https://www.bilibili.com/read/cv123"),
                delegate("https://cdn.example/video.m4s")
        );
        TestSource source = new TestSource();
        assertNull(provider.from("https://b23.tv/example", source).get());
        assertTrue(source.messages.stream().anyMatch(message -> message.contains("supported Bilibili video")));
        assertNull(provider.from("https://b23.tv.evil.example/example", source));
        assertFalse(B23VideoProvider.isSupportedVideo(URI.create("https://evil.example/video/BV1p98q6dErq")));
        provider.close();
    }

    @Test
    void rejectsClientSideFallback() throws Exception {
        B23VideoProvider provider = new B23VideoProvider(
                DIRECT,
                uri -> URI.create("https://www.bilibili.com/video/BV1p98q6dErq"),
                delegate("")
        );
        TestSource source = new TestSource();
        assertNull(provider.from("https://b23.tv/example", source).get());
        assertTrue(source.messages.stream().anyMatch(message -> message.contains("could not be resolved on the server")));
        provider.close();
    }

    private static BiliBiliVideoProvider delegate(String path) {
        return new BiliBiliVideoProvider() {
            @Override
            public @Nullable CompletableFuture<VideoInfo> from(String str, IProviderSource source) {
                return CompletableFuture.completedFuture(new VideoInfo(
                        source.name(), "Bilibili", path, "BV1p98q6dErq", -1, true, new String[0]
                ));
            }
        };
    }

    private static final class TestSource implements IProviderSource {
        private final List<String> messages = new ArrayList<>();

        @Override
        public String name() {
            return "tester";
        }

        @Override
        public void reply(String text) {
            messages.add(text);
        }
    }
}
