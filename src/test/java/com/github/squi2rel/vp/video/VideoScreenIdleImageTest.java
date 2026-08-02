package com.github.squi2rel.vp.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoScreenIdleImageTest {
    @Test
    void hidesFallbackWhenIdleImageIsDisabledAndPlaybackIsIdle() {
        assertFalse(VideoScreen.shouldKeepFallbackFrame(false, false));
    }

    @Test
    void keepsFallbackWhenIdleImageIsEnabledAndPlaybackIsIdle() {
        assertTrue(VideoScreen.shouldKeepFallbackFrame(false, true));
    }

    @Test
    void keepsFallbackDuringPlaybackLoadingWhenIdleImageIsDisabled() {
        assertTrue(VideoScreen.shouldKeepFallbackFrame(true, false));
    }

    @Test
    void keepsFallbackDuringPlaybackLoadingWhenIdleImageIsEnabled() {
        assertTrue(VideoScreen.shouldKeepFallbackFrame(true, true));
    }
}
