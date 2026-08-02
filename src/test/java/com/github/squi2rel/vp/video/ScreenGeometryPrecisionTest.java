package com.github.squi2rel.vp.video;

import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenGeometryPrecisionTest {
    @Test
    void translatedGeometryKeepsLocalShapeProjectionAndTriangulation() {
        List<Vector3f> near = List.of(
                new Vector3f(0.0f, 0.0f, 0.0f),
                new Vector3f(4.0f, 0.0f, 0.0f),
                new Vector3f(4.0f, 3.0f, 0.0f),
                new Vector3f(2.0f, 1.5f, 0.0f),
                new Vector3f(0.0f, 3.0f, 0.0f)
        );
        List<Vector3f> far = translate(near, 500_000.0f, 64.0f, 300_000.0f);

        ScreenGeometry nearGeometry = ScreenGeometry.create(near);
        ScreenGeometry farGeometry = ScreenGeometry.create(far);

        assertEquals(nearGeometry.localVertices().size(), farGeometry.localVertices().size());
        for (int i = 0; i < nearGeometry.localVertices().size(); i++) {
            assertVectorEquals(nearGeometry.localVertices().get(i), farGeometry.localVertices().get(i), 0.0f);
            assertVectorEquals(nearGeometry.normal(), farGeometry.normal(), 0.0f);
            assertPointEquals(nearGeometry.projectedPoint(i), farGeometry.projectedPoint(i), 0.0f);
            assertPointEquals(nearGeometry.editPoint(i), farGeometry.editPoint(i), 0.0f);
        }
        assertArrayEquals(nearGeometry.triangles(), farGeometry.triangles());
    }

    @Test
    void cameraRelativeOriginTracksSubBlockMovementAtFarCoordinates() {
        ScreenGeometry geometry = ScreenGeometry.create(List.of(
                new Vector3f(500_000.0f, 64.0f, 300_000.0f),
                new Vector3f(500_004.0f, 64.0f, 300_000.0f),
                new Vector3f(500_004.0f, 67.0f, 300_000.0f),
                new Vector3f(500_000.0f, 67.0f, 300_000.0f)
        ));

        float previous = geometry.relativeOrigin(499_999.9, 64.0, 300_000.0).x;
        for (int i = 1; i <= 100; i++) {
            float current = geometry.relativeOrigin(499_999.9 + i * 0.001, 64.0, 300_000.0).x;
            assertTrue(current < previous);
            assertEquals(-0.001f, current - previous, 0.000001f);
            previous = current;
        }
    }

    @Test
    void doublePrecisionRayHitsFarScreenAndPreservesHitPoint() {
        ScreenGeometry geometry = ScreenGeometry.create(List.of(
                new Vector3f(500_000.0f, 64.0f, 300_000.0f),
                new Vector3f(500_004.0f, 64.0f, 300_000.0f),
                new Vector3f(500_004.0f, 68.0f, 300_000.0f),
                new Vector3f(500_000.0f, 68.0f, 300_000.0f)
        ));
        Vector3d start = new Vector3d(500_002.0001, 66.0001, 299_990.12345);
        Vector3d end = new Vector3d(500_002.0001, 66.0001, 300_010.12345);
        Vector3d hit = new Vector3d();

        assertTrue(geometry.intersectsRay(start, end, hit));
        assertEquals(500_002.0001, hit.x, 0.0000001);
        assertEquals(66.0001, hit.y, 0.0000001);
        assertEquals(300_000.0, hit.z, 0.0000001);

        Vector3d outsideStart = new Vector3d(500_004.1, 66.0, 299_990.0);
        Vector3d outsideEnd = new Vector3d(500_004.1, 66.0, 300_010.0);
        assertFalse(geometry.intersectsRay(outsideStart, outsideEnd, new Vector3d()));
    }

    @Test
    void curvedStripUsesUnfoldedArcLengthAndTriangleRayIntersection() {
        ScreenGeometry geometry = ScreenGeometry.create(curvedStrip());

        assertEquals(8, geometry.triangles().length / 3);
        assertEquals(0.0f, geometry.editPoint(0).x, 0.0001f);
        assertTrue(geometry.editPoint(4).x > geometry.width());
        assertEquals(geometry.editPoint(4).x, geometry.editPoint(5).x, 0.0001f);
        assertEquals(0.0f, geometry.editPoint(4).y, 0.0001f);
        assertEquals(3.0f, geometry.editPoint(5).y, 0.0001f);

        Vector3d hit = new Vector3d();
        assertTrue(geometry.intersectsRay(
                new Vector3d(0.0, 1.5, -10.0),
                new Vector3d(0.0, 1.5, 10.0),
                hit
        ));
        assertEquals(0.0, hit.x, 0.000001);
        assertEquals(1.5, hit.y, 0.000001);
        assertEquals(0.0, hit.z, 0.000001);
    }

    private static List<Vector3f> translate(List<Vector3f> vertices, float x, float y, float z) {
        ArrayList<Vector3f> translated = new ArrayList<>(vertices.size());
        for (Vector3f vertex : vertices) {
            translated.add(new Vector3f(vertex).add(x, y, z));
        }
        return translated;
    }

    private static List<Vector3f> curvedStrip() {
        ArrayList<Vector3f> vertices = new ArrayList<>();
        for (int degree : new int[]{-60, -30, 0, 30, 60}) {
            double radians = Math.toRadians(degree);
            vertices.add(new Vector3f(
                    (float) (5.0 * Math.sin(radians)),
                    3.0f,
                    (float) (5.0 * (1.0 - Math.cos(radians)))
            ));
        }
        for (int degree : new int[]{60, 30, 0, -30, -60}) {
            double radians = Math.toRadians(degree);
            vertices.add(new Vector3f(
                    (float) (5.0 * Math.sin(radians)),
                    0.0f,
                    (float) (5.0 * (1.0 - Math.cos(radians)))
            ));
        }
        return vertices;
    }

    private static void assertVectorEquals(Vector3f expected, Vector3f actual, float delta) {
        assertEquals(expected.x, actual.x, delta);
        assertEquals(expected.y, actual.y, delta);
        assertEquals(expected.z, actual.z, delta);
    }

    private static void assertPointEquals(Vector2f expected, Vector2f actual, float delta) {
        assertEquals(expected.x, actual.x, delta);
        assertEquals(expected.y, actual.y, delta);
    }
}
