package com.github.squi2rel.vp.video;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;

class VideoAreaBoundsTest {
    @Test
    void usesExclusiveMaximumForPlayerMembership() {
        VideoArea area = new VideoArea(new Vector3f(1, 2, 3), new Vector3f(5, 6, 7), "area", "world");

        assertTrue(area.inBounds(new Vec3(1, 2, 3)));
        assertTrue(area.inBounds(new Vec3(4.999, 5.999, 6.999)));
        assertFalse(area.inBounds(new Vec3(5, 5, 6)));
        assertFalse(area.inBounds(new Vec3(4, 6, 6)));
        assertFalse(area.inBounds(new Vec3(4, 5, 7)));
    }
}
