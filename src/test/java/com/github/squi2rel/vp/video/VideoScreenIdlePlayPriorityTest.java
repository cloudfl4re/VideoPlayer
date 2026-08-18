package com.github.squi2rel.vp.video;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class VideoScreenIdlePlayPriorityTest {
    @Test
    void sequentialPlaybackUsesDescendingPriorityAndStableInsertionOrder() {
        VideoScreen screen = screen();
        screen.setIdlePlayEntries(List.of(
                entry("low", "first", 10),
                entry("high-a", "second", 90),
                entry("high-b", "third", 90)
        ), false);

        assertEquals("high-a", screen.nextIdlePlayUrl());
        assertEquals("high-b", screen.nextIdlePlayUrl());
        assertEquals("low", screen.nextIdlePlayUrl());
        assertEquals("high-a", screen.nextIdlePlayUrl());
    }

    @Test
    void randomPlaybackOnlyShufflesWithinEachPriorityGroup() {
        VideoScreen screen = screen();
        screen.setIdlePlayEntries(List.of(
                entry("low", "first", 10),
                entry("high-a", "second", 90),
                entry("high-b", "third", 90)
        ), true);

        HashSet<String> high = new HashSet<>(List.of(screen.nextIdlePlayUrl(), screen.nextIdlePlayUrl()));
        assertEquals(new HashSet<>(List.of("high-a", "high-b")), high);
        assertEquals("low", screen.nextIdlePlayUrl());
    }

    @Test
    void migratesLegacyUrlsWithoutInventingAnOwner() {
        VideoScreen screen = screen();
        screen.idlePlayUrls.add("https://www.bilibili.com/video/BV1xx411c7mD/?vd_source=test");

        screen.ensureValidState();

        assertTrue(screen.idlePlayUrls.isEmpty());
        IdlePlayEntry entry = screen.idlePlayEntries.getFirst();
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD", entry.url());
        assertTrue(entry.legacyOwner());
        assertEquals(0, entry.priority());
    }

    @Test
    void serverMutationCreatesTheOwnerAndPriorityUpdatesPreserveIt() {
        VideoScreen screen = screen();
        UUID owner = UUID.randomUUID();

        assertTrue(screen.applyIdlePlayMutation(
                com.github.squi2rel.vp.network.IdlePlayMutation.add("same", 80), owner, "player"
        ));
        assertTrue(screen.applyIdlePlayMutation(
                com.github.squi2rel.vp.network.IdlePlayMutation.add("same", 20), UUID.randomUUID(), "other"
        ));
        IdlePlayEntry first = screen.idlePlayEntries.getFirst();
        assertTrue(screen.applyIdlePlayMutation(
                com.github.squi2rel.vp.network.IdlePlayMutation.setPriority(first.id(), 55), UUID.randomUUID(), "spoofed"
        ));

        assertEquals(2, screen.idlePlayEntries.size());
        IdlePlayEntry updated = screen.idlePlayEntries.getFirst();
        assertEquals(first.id(), updated.id());
        assertEquals(owner, updated.addedBy());
        assertEquals("player", updated.addedByName());
        assertEquals(55, updated.priority());
    }

    @Test
    void rejectsMissingEntriesAndTheThirtyThirdAddition() {
        VideoScreen screen = screen();
        UUID owner = UUID.randomUUID();
        for (int i = 0; i < VideoScreen.MAX_IDLE_PLAY_ITEMS; i++) {
            assertTrue(screen.addIdlePlayEntry("video-" + i, owner, "player", 0));
        }

        assertFalse(screen.addIdlePlayEntry("overflow", owner, "player", 0));
        assertFalse(screen.removeIdlePlayEntry(UUID.randomUUID()));
        assertFalse(screen.setIdlePlayPriority(UUID.randomUUID(), 50));
        assertFalse(screen.setIdlePlayPriority(screen.idlePlayEntries.getFirst().id(), 101));
    }

    private static IdlePlayEntry entry(String url, String player, int priority) {
        return new IdlePlayEntry(UUID.randomUUID(), url, UUID.randomUUID(), player, priority);
    }

    private static VideoScreen screen() {
        VideoArea area = new VideoArea(new Vector3f(), new Vector3f(1), "area", "world");
        return new VideoScreen(area, "screen", List.of(
                new Vector3f(), new Vector3f(1, 0, 0), new Vector3f(1, 1, 0), new Vector3f(0, 1, 0)
        ), "");
    }
}
