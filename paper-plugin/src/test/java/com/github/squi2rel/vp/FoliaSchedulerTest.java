package com.github.squi2rel.vp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoliaSchedulerTest {
    @Test
    void entityAndRegionSchedulingNeverUseZeroTicks() {
        assertEquals(1L, FoliaScheduler.minimumEntityDelay(-4L));
        assertEquals(1L, FoliaScheduler.minimumEntityDelay(0L));
        assertEquals(1L, FoliaScheduler.minimumEntityDelay(1L));
        assertEquals(12L, FoliaScheduler.minimumEntityDelay(12L));
    }
}
