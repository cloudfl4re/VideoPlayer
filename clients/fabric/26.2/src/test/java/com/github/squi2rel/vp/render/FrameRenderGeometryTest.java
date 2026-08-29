package com.github.squi2rel.vp.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FrameRenderGeometryTest {
    @Test
    void copiesMutableVertexArraysAtSnapshotBoundary() {
        float[] positions = {1.0f, 2.0f, 3.0f};
        float[] uvs = {0.25f, 0.75f};
        float[] normals = {0.0f, 1.0f, 0.0f};
        float[] lineWidths = {1.0f};
        int[] colors = {0xFF112233};
        int[] lights = {0x00F000F0};
        int[] overlays = {0};
        int[] attributes = {FrameRenderGeometry.UV | FrameRenderGeometry.NORMAL};

        FrameRenderGeometry snapshot = new FrameRenderGeometry(
                positions,
                uvs,
                normals,
                lineWidths,
                colors,
                lights,
                overlays,
                attributes
        );
        positions[0] = 99.0f;
        colors[0] = 0;

        assertEquals(1.0f, snapshot.x(0));
        assertEquals(0xFF112233, snapshot.color(0));
    }
}
