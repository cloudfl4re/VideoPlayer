package com.github.squi2rel.vp.render;

import java.util.Objects;

public final class FrameRenderGeometry {
    public static final int UV = 1;
    public static final int NORMAL = 1 << 1;
    public static final int LIGHT = 1 << 2;
    public static final int OVERLAY = 1 << 3;
    public static final int LINE_WIDTH = 1 << 4;

    private final float[] positions;
    private final float[] uvs;
    private final float[] normals;
    private final float[] lineWidths;
    private final int[] colors;
    private final int[] lights;
    private final int[] overlays;
    private final int[] attributes;

    public FrameRenderGeometry(float[] positions, float[] uvs, float[] normals, float[] lineWidths,
                               int[] colors, int[] lights, int[] overlays, int[] attributes) {
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(uvs, "uvs");
        Objects.requireNonNull(normals, "normals");
        Objects.requireNonNull(lineWidths, "lineWidths");
        Objects.requireNonNull(colors, "colors");
        Objects.requireNonNull(lights, "lights");
        Objects.requireNonNull(overlays, "overlays");
        Objects.requireNonNull(attributes, "attributes");
        if (positions.length % 3 != 0) throw new IllegalArgumentException("positions");
        int count = positions.length / 3;
        if (uvs.length != count * 2
                || normals.length != count * 3
                || lineWidths.length != count
                || colors.length != count
                || lights.length != count
                || overlays.length != count
                || attributes.length != count) {
            throw new IllegalArgumentException("vertex attributes");
        }
        this.positions = positions.clone();
        this.uvs = uvs.clone();
        this.normals = normals.clone();
        this.lineWidths = lineWidths.clone();
        this.colors = colors.clone();
        this.lights = lights.clone();
        this.overlays = overlays.clone();
        this.attributes = attributes.clone();
    }

    public int vertexCount() {
        return colors.length;
    }

    public float x(int vertex) {
        return positions[vertex * 3];
    }

    public float y(int vertex) {
        return positions[vertex * 3 + 1];
    }

    public float z(int vertex) {
        return positions[vertex * 3 + 2];
    }

    public float u(int vertex) {
        return uvs[vertex * 2];
    }

    public float v(int vertex) {
        return uvs[vertex * 2 + 1];
    }

    public float normalX(int vertex) {
        return normals[vertex * 3];
    }

    public float normalY(int vertex) {
        return normals[vertex * 3 + 1];
    }

    public float normalZ(int vertex) {
        return normals[vertex * 3 + 2];
    }

    public float lineWidth(int vertex) {
        return lineWidths[vertex];
    }

    public int color(int vertex) {
        return colors[vertex];
    }

    public int light(int vertex) {
        return lights[vertex];
    }

    public int overlay(int vertex) {
        return overlays[vertex];
    }

    public boolean has(int vertex, int attribute) {
        return (attributes[vertex] & attribute) != 0;
    }
}
