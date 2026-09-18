package com.github.squi2rel.vp.permission;

import com.github.squi2rel.vp.video.ScreenSurface;
import com.github.squi2rel.vp.video.VideoArea;
import com.github.squi2rel.vp.video.VideoScreen;
import org.joml.Vector3f;

public record VideoPermissionContext(
        String dimension,
        String areaName,
        String screenName,
        Position areaMin,
        Position areaMax,
        Position anchor,
        Position screenMin,
        Position screenMax
) {
    public VideoPermissionContext(
            String dimension,
            String areaName,
            String screenName,
            Position areaMin,
            Position areaMax,
            Position anchor
    ) {
        this(dimension, areaName, screenName, areaMin, areaMax, anchor, null, null);
    }

    public static VideoPermissionContext global(String dimension) {
        return new VideoPermissionContext(dimension, null, null, null, null, null, null, null);
    }

    public static VideoPermissionContext area(VideoArea area) {
        if (area == null) return global(null);
        return bounds(area.dim, area.name, area.min, area.max);
    }

    public static VideoPermissionContext bounds(String dimension, String areaName, Vector3f first, Vector3f second) {
        return new VideoPermissionContext(
                dimension,
                areaName,
                null,
                Position.from(first),
                Position.from(second),
                null,
                null,
                null
        );
    }

    public static VideoPermissionContext screen(VideoScreen screen) {
        if (screen == null) return global(null);
        VideoArea area = screen.area;
        Vector3f anchor = null;
        Vector3f screenMin = null;
        Vector3f screenMax = null;
        if (screen.surface == ScreenSurface.SPHERE_360 && screen.spherePreset && screen.sphereCenter != null) {
            anchor = screen.sphereCenter;
            if (Float.isFinite(screen.sphereRadius) && screen.sphereRadius > 0
                    && Float.isFinite(anchor.x) && Float.isFinite(anchor.y) && Float.isFinite(anchor.z)) {
                float radius = screen.sphereRadius;
                screenMin = new Vector3f(anchor.x - radius, anchor.y - radius, anchor.z - radius);
                screenMax = new Vector3f(anchor.x + radius, anchor.y + radius, anchor.z + radius);
            }
        } else if (screen.vertices != null && !screen.vertices.isEmpty()) {
            anchor = screen.vertices.getFirst();
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            boolean valid = true;
            for (Vector3f vertex : screen.vertices) {
                if (vertex == null || !Float.isFinite(vertex.x) || !Float.isFinite(vertex.y) || !Float.isFinite(vertex.z)) {
                    valid = false;
                    break;
                }
                minX = Math.min(minX, vertex.x);
                minY = Math.min(minY, vertex.y);
                minZ = Math.min(minZ, vertex.z);
                maxX = Math.max(maxX, vertex.x);
                maxY = Math.max(maxY, vertex.y);
                maxZ = Math.max(maxZ, vertex.z);
            }
            if (valid) {
                screenMin = new Vector3f(minX, minY, minZ);
                screenMax = new Vector3f(maxX, maxY, maxZ);
            }
        }
        return new VideoPermissionContext(
                area == null ? null : area.dim,
                area == null ? null : area.name,
                screen.name,
                area == null ? null : Position.from(area.min),
                area == null ? null : Position.from(area.max),
                Position.from(anchor),
                Position.from(screenMin),
                Position.from(screenMax)
        );
    }

    public boolean hasArea() {
        return areaName != null && !areaName.isBlank();
    }

    public boolean hasBounds() {
        return areaMin != null && areaMax != null;
    }

    public boolean hasScreenBounds() {
        return screenMin != null && screenMax != null;
    }

    public record Position(float x, float y, float z) {
        public static Position from(Vector3f value) {
            return value == null ? null : new Position(value.x, value.y, value.z);
        }
    }
}
