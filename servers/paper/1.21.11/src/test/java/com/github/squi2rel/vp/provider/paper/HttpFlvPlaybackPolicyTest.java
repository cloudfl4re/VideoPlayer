package com.github.squi2rel.vp.provider.paper;

import com.github.squi2rel.vp.provider.VideoInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpFlvPlaybackPolicyTest {
    @Test
    void recognizesHttpFlvWithQuery() {
        assertTrue(HttpFlvPlaybackPolicy.isHttpFlv("https://stream.example/live.flv?token=abc"));
    }

    @Test
    void recognizesUppercaseHttpFlv() {
        assertTrue(HttpFlvPlaybackPolicy.isHttpFlv("HTTP://STREAM.EXAMPLE/LIVE.FLV"));
    }

    @Test
    void rejectsNonFlvUrl() {
        assertFalse(HttpFlvPlaybackPolicy.isHttpFlv("https://stream.example/live.mp4?format=flv"));
    }

    @Test
    void normalizesResolvedHttpFlvAsNonSeekableLiveMedia() {
        String[] params = {"user-agent=VideoPlayer"};
        VideoInfo input = new VideoInfo("player", "stream", "https://stream.example/live.flv?token=abc", "", -1L,
                true, params, 120_000L);

        VideoInfo normalized = HttpFlvPlaybackPolicy.normalize(input);

        assertFalse(normalized.seekable());
        assertEquals(0L, normalized.durationMs());
        assertArrayEquals(params, normalized.params());
        assertEquals(input.path(), normalized.path());
    }

    @Test
    void leavesNonFlvInfoUnchanged() {
        VideoInfo input = new VideoInfo("player", "video", "https://stream.example/video.mp4", "", -1L,
                true, new String[0], 120_000L);

        assertSame(input, HttpFlvPlaybackPolicy.normalize(input));
    }
}
