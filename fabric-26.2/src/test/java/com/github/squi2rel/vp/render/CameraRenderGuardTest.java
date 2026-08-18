package com.github.squi2rel.vp.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CameraRenderGuardTest {
    @Test
    void rejectsRecursionAndRestoresStateAfterFailure() {
        CameraRenderGuard guard = new CameraRenderGuard();

        try {
            try (CameraRenderGuard.Scope ignored = guard.enter()) {
                assertTrue(guard.isRendering());
                assertNull(guard.enter());
                throw new IllegalStateException("render failed");
            }
        } catch (IllegalStateException ignored) {
        }

        assertFalse(guard.isRendering());
    }
}
