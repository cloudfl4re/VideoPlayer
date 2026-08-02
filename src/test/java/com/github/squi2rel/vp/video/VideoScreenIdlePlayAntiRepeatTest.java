package com.github.squi2rel.vp.video;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoScreenIdlePlayAntiRepeatTest {
    @Test
    void randomOrderNeverRepeatsTheLastPlayedEntryAcrossRebuilds() {
        VideoScreen screen = screen();
        screen.setIdlePlayEntries(List.of(
                entry("a", 0),
                entry("b", 0),
                entry("c", 0)
        ), true);

        String previous = screen.nextIdlePlayUrl();
        for (int i = 0; i < 300; i++) {
            String current = screen.nextIdlePlayUrl();
            assertNotEquals(previous, current);
            previous = current;
        }
    }

    @Test
    void antiRepeatOnlySwapsWithinTheLeadingPriorityGroup() {
        VideoScreen screen = screen();
        screen.setIdlePlayEntries(List.of(
                entry("high", 90),
                entry("low", 10)
        ), true);

        for (int i = 0; i < 5; i++) {
            assertEquals("high", screen.nextIdlePlayUrl());
            assertEquals("low", screen.nextIdlePlayUrl());
        }
    }

    @Test
    void adjustPriorityClampsAgainstTheAuthoritativeValueAndRejectsMissingEntries() {
        VideoScreen screen = screen();
        screen.setIdlePlayEntries(List.of(entry("video", IdlePlayEntry.MAX_PRIORITY - 1)), false);
        UUID id = screen.idlePlayEntries.getFirst().id();

        assertTrue(screen.adjustIdlePlayPriority(id, 5));
        assertEquals(IdlePlayEntry.MAX_PRIORITY, screen.idlePlayEntries.getFirst().priority());
        assertTrue(screen.adjustIdlePlayPriority(id, -30));
        assertEquals(IdlePlayEntry.MAX_PRIORITY - 30, screen.idlePlayEntries.getFirst().priority());
        assertTrue(screen.adjustIdlePlayPriority(id, -IdlePlayEntry.MAX_PRIORITY));
        assertEquals(IdlePlayEntry.MIN_PRIORITY, screen.idlePlayEntries.getFirst().priority());
        assertFalse(screen.adjustIdlePlayPriority(UUID.randomUUID(), 1));
    }

    private static IdlePlayEntry entry(String url, int priority) {
        return new IdlePlayEntry(UUID.randomUUID(), url, UUID.randomUUID(), "player", priority);
    }

    private static VideoScreen screen() {
        VideoArea area = new VideoArea(new Vector3f(), new Vector3f(1), "area", "world");
        return new VideoScreen(area, "screen", List.of(
                new Vector3f(), new Vector3f(1, 0, 0), new Vector3f(1, 1, 0), new Vector3f(0, 1, 0)
        ), "");
    }
}
