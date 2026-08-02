package com.github.squi2rel.vp.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoParamsMediaUrlTest {
    @Test
    void rejectsDisallowedAudioAndSubtitleUrls() {
        String blockedAudio = "audio-file=http://127.0.0.1/audio.m4a";
        String blockedSubtitle = VideoParams.subtitleParam(
                "yt:sub:en:0",
                "English",
                "en",
                "http://169.254.169.254/latest/meta-data",
                "https://www.youtube.com",
                false
        );
        String allowedAudio = "audio-file=https://8.8.8.8/audio.m4a";

        assertTrue(VideoParams.hasDisallowedMediaUrls(new String[]{blockedAudio}));
        assertTrue(VideoParams.hasDisallowedMediaUrls(new String[]{blockedSubtitle}));
        assertFalse(VideoParams.hasDisallowedMediaUrls(new String[]{allowedAudio, "ytdl=no"}));
    }

    @Test
    void excludesLegacyLiveChatSubtitleParameters() {
        String liveChat = VideoParams.subtitleParam(
                "yt:sub:live_chat:0",
                "live_chat",
                "live_chat",
                "https://www.youtube.com/watch?v=test",
                "https://www.youtube.com/watch?v=test",
                false
        );
        String english = VideoParams.subtitleParam(
                "yt:sub:en:1",
                "English",
                "en",
                "https://subtitle.example/en.vtt",
                "https://www.youtube.com/watch?v=test",
                false
        );

        assertEquals(1, VideoParams.subtitleParams(new String[]{liveChat, english}).size());
        assertEquals("en", VideoParams.subtitleParams(new String[]{liveChat, english}).getFirst().language());
    }
}
