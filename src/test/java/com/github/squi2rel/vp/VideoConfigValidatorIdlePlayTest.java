package com.github.squi2rel.vp;

import com.github.squi2rel.vp.video.IdlePlayEntry;
import com.github.squi2rel.vp.video.VideoArea;
import com.github.squi2rel.vp.video.VideoScreen;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VideoConfigValidatorIdlePlayTest {
    @Test
    void rejectsPersistedEntriesOverTheTotalByteLimitWithoutTruncatingThem() {
        VideoArea area = area();
        VideoScreen screen = screen(area);
        for (int i = 0; i < 25; i++) {
            screen.idlePlayEntries.add(entry("x".repeat(1_000)));
        }

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> VideoConfigValidator.validateArea(area));

        assertEquals("VideoPlayer IdlePlay configuration exceeds protocol limits", error.getMessage());
        assertEquals(25, screen.idlePlayEntries.size());
    }

    @Test
    void rejectsPersistedEntriesOverTheItemCountLimitWithoutTruncatingThem() {
        VideoArea area = area();
        VideoScreen screen = screen(area);
        for (int i = 0; i < VideoScreen.MAX_IDLE_PLAY_ITEMS + 1; i++) {
            screen.idlePlayEntries.add(entry("https://example.invalid/" + i));
        }

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> VideoConfigValidator.validateArea(area));

        assertEquals("VideoPlayer IdlePlay configuration exceeds protocol limits", error.getMessage());
        assertEquals(VideoScreen.MAX_IDLE_PLAY_ITEMS + 1, screen.idlePlayEntries.size());
    }

    @Test
    void rejectsPersistedEntriesWithAnOverLongUrl() {
        VideoArea area = area();
        VideoScreen screen = screen(area);
        screen.idlePlayEntries.add(entry("x".repeat(VideoScreen.MAX_IDLE_PLAY_URL_BYTES + 1)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> VideoConfigValidator.validateArea(area));

        assertEquals("VideoPlayer IdlePlay configuration exceeds protocol limits", error.getMessage());
        assertEquals(1, screen.idlePlayEntries.size());
    }

    @Test
    void acceptsPersistedEntriesExactlyAtTheProtocolLimits() {
        VideoArea area = area();
        VideoScreen screen = screen(area);
        int blocks = VideoScreen.MAX_IDLE_PLAY_TOTAL_BYTES / VideoScreen.MAX_IDLE_PLAY_URL_BYTES;
        for (int i = 0; i < blocks; i++) {
            screen.idlePlayEntries.add(entry("x".repeat(VideoScreen.MAX_IDLE_PLAY_URL_BYTES)));
        }
        screen.idlePlayEntries.add(entry("x".repeat(VideoScreen.MAX_IDLE_PLAY_TOTAL_BYTES - blocks * VideoScreen.MAX_IDLE_PLAY_URL_BYTES)));

        assertDoesNotThrow(() -> VideoConfigValidator.validateArea(area));
        assertEquals(blocks + 1, screen.idlePlayEntries.size());
    }

    private static VideoArea area() {
        return new VideoArea(new Vector3f(), new Vector3f(16, 16, 16), "area", "minecraft:overworld");
    }

    private static VideoScreen screen(VideoArea area) {
        VideoScreen screen = new VideoScreen(area, "screen", List.of(
                new Vector3f(0, 0, 0),
                new Vector3f(1, 0, 0),
                new Vector3f(1, 1, 0),
                new Vector3f(0, 1, 0)
        ), "");
        area.addScreen(screen);
        return screen;
    }

    private static IdlePlayEntry entry(String url) {
        return new IdlePlayEntry(UUID.randomUUID(), url, UUID.randomUUID(), "player", 0);
    }
}
