package com.github.squi2rel.vp.video;

import com.github.squi2rel.vp.provider.VideoInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoListenersTest {
    @Test
    void onlyUnknownDurationServerStreamsRequireNativeListener() {
        VideoInfo knownDuration = info("https://example.com/video.mp4", "", 1_000);
        VideoInfo clientResolved = info("", "https://example.com/watch", 0);
        VideoInfo unknownDurationStream = info("rtsp://example.com/live", "rtsp://example.com/live", 0);

        assertFalse(VideoListeners.requiresNativeStreamListener(knownDuration));
        assertFalse(VideoListeners.requiresNativeStreamListener(clientResolved));
        assertTrue(VideoListeners.requiresNativeStreamListener(unknownDurationStream));
    }

    @Test
    void youtubeLiveUsesClientResolutionListenerWithoutNativeBackend() {
        VideoInfo live = new VideoInfo("player", "live", "https://video.example/live",
                "https://www.youtube.com/watch?v=live", -1, false, new String[0], 0);

        assertFalse(VideoListeners.requiresNativeStreamListener(live));
        assertFalse(VideoListeners.awaitsClientPlaybackResolution(live));
        assertTrue(VideoListeners.from(live) instanceof ClientResolutionStreamListener);
    }

    @Test
    void youtubeClientFallbackWithUnknownDurationAwaitsLocalResolution() {
        VideoInfo fallback = new VideoInfo("player", "video", "",
                "https://www.youtube.com/watch?v=fallback", -1, true, new String[0], 0);

        assertFalse(VideoListeners.requiresNativeStreamListener(fallback));
        assertTrue(VideoListeners.awaitsClientPlaybackResolution(fallback));
        assertTrue(VideoListeners.from(fallback) instanceof ClientResolutionStreamListener);
    }

    @Test
    void bilibiliWithResolvedPathUsesNativeListenerWhenDurationUnknown() {
        VideoInfo resolved = new VideoInfo(
                "player",
                "bili",
                "https://upos.example/video.m4s",
                "https://www.bilibili.com/video/BV1xx411c7mD",
                -1,
                true,
                new String[0],
                0
        );
        VideoInfo clientOnly = new VideoInfo(
                "player",
                "bili",
                "",
                "https://www.bilibili.com/video/BV1xx411c7mD",
                -1,
                true,
                new String[0],
                0
        );

        assertTrue(VideoListeners.requiresNativeStreamListener(resolved));
        assertFalse(VideoListeners.requiresNativeStreamListener(clientOnly));
    }

    private static VideoInfo info(String path, String rawPath, long duration) {
        return new VideoInfo("player", "video", path, rawPath, -1, false, new String[0], duration);
    }
}
