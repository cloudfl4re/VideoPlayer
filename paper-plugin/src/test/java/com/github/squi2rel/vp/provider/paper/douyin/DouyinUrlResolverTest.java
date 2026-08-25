package com.github.squi2rel.vp.provider.paper.douyin;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DouyinUrlResolverTest {
    @Test
    void extractsVideoFromShareText() throws Exception {
        DouyinUrlResolver resolver = new DouyinUrlResolver(uri -> uri);
        URI submitted = resolver.submitted("1.23 abc https://www.douyin.com/video/7670394671851523382 复制链接");
        DouyinUrlResolver.Target target = resolver.resolve(submitted);
        assertEquals(DouyinUrlResolver.Kind.VIDEO, target.kind());
        assertEquals("7670394671851523382", target.id());
        assertEquals("https://www.douyin.com/video/7670394671851523382", target.canonicalUrl());
    }

    @Test
    void expandsShortLiveLink() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        DouyinUrlResolver resolver = new DouyinUrlResolver(uri -> {
            calls.incrementAndGet();
            return URI.create("https://live.douyin.com/598003708158");
        });
        DouyinUrlResolver.Target target = resolver.resolve(resolver.submitted("https://v.douyin.com/example/"));
        assertEquals(1, calls.get());
        assertEquals(DouyinUrlResolver.Kind.LIVE, target.kind());
        assertEquals("598003708158", target.id());
    }

    @Test
    void rejectsLookalikeAndUnsupportedContent() {
        DouyinUrlResolver resolver = new DouyinUrlResolver(uri -> uri);
        assertNull(resolver.submitted("https://v.douyin.com.evil.example/code"));
        assertNull(resolver.submitted("https://www.douyin.com/note/7670394671851523382"));
        assertNull(resolver.submitted("https://user@www.douyin.com/video/7670394671851523382"));
        assertNull(resolver.submitted("https://www.douyin.com:8443/video/7670394671851523382"));
    }
}
