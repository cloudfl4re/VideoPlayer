package com.github.squi2rel.vp.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VideoInfoLiveMediaTest {
    @Test
    void httpFlvIsAlwaysLive() {
        VideoInfo info = new VideoInfo("player", "stream", "https://stream.example/live.flv", "", -1L,
                true, new String[0], 120_000L);

        assertFalse(info.seekable());
        assertEquals(0L, info.durationMs());
    }

    @Test
    void srtIsAlwaysLive() {
        VideoInfo info = new VideoInfo("player", "stream", "srt://stream.example:9000?mode=caller", "", -1L,
                true, new String[0], 120_000L);

        assertFalse(info.seekable());
        assertEquals(0L, info.durationMs());
    }
}
