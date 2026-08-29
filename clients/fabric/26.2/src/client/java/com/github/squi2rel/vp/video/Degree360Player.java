package com.github.squi2rel.vp.video;

import com.github.squi2rel.vp.ScreenRenderer;
import com.github.squi2rel.vp.render.WorldRenderBatch;
import com.github.squi2rel.vp.vivecraft.Vivecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.Mth;

import static com.github.squi2rel.vp.VideoPlayerClient.config;

public final class Degree360Player {
    private static final Quaternionf tmp = new Quaternionf();
    private static final int MAX_CACHED_MESHES = 24;
    private static final LinkedHashMap<MeshKey, float[]> MESHES = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<MeshKey, float[]> eldest) {
            return size() > MAX_CACHED_MESHES;
        }
    };

    private Degree360Player() {
    }

    public static void drawTexture(int textureId, PoseStack matrices, WorldRenderBatch consumers, ClientVideoScreen screen) {
        drawTexture(textureId, matrices, consumers, screen, screen.stereo3d);
    }

    public static void drawTexture(int textureId, PoseStack matrices, WorldRenderBatch consumers, ClientVideoScreen screen, boolean is3d) {
        if (textureId < 0) return;
        boolean rightEye = is3d && Vivecraft.loaded && Vivecraft.isVRActive() && Vivecraft.isRightEye();
        float[] mesh = meshFor(screen, is3d, rightEye);
        if (mesh == null || mesh.length == 0) return;

        matrices.pushPose();
        if (screen.sphereSkybox) {
            ScreenRenderer.skybox = true;
        } else {
            Vector3f center = screen.sphereCenter == null ? new Vector3f() : screen.sphereCenter;
            matrices.translate(
                    center.x - ScreenRenderer.preciseCameraX,
                    center.y - ScreenRenderer.preciseCameraY,
                    center.z - ScreenRenderer.preciseCameraZ
            );
        }
        applySphereRotation(matrices, screen.sphereRotX, screen.sphereRotY, screen.sphereRotZ);
        Matrix4f matrix = new Matrix4f(matrices.last().pose());
        matrices.popPose();

        int gray = (int) (config.brightness / 100.0 * 255);
        int color = 0xFF000000 | (gray << 16) | (gray << 8) | gray;
        VertexConsumer consumer = consumers.getBuffer(ScreenRenderer.getLayer(textureId));
        appendSphereQuads(consumer, matrix, mesh, clampSegments(screen.sphereLat), clampSegments(screen.sphereLon),
                is3d, rightEye, screen.u1, screen.u2, color);
    }

    public static void clearMeshCache() {
        MESHES.clear();
    }

    private static float[] meshFor(ClientVideoScreen screen, boolean stereo3d, boolean rightEye) {
        int latSegments = clampSegments(screen.sphereLat);
        int lonSegments = clampSegments(screen.sphereLon);
        MeshKey key = MeshKey.of(screen, stereo3d, rightEye, latSegments, lonSegments);
        float[] cached = MESHES.get(key);
        if (cached != null) return cached;
        float[] mesh = key.hemisphere
                ? genHemisphereVertices(key.radius(), latSegments, lonSegments, key.u1(), key.u2(), key.v1(), key.v2())
                : genVertices(key.radius(), latSegments, lonSegments, key.u1(), key.u2(), key.v1(), key.v2());
        MESHES.put(key, mesh);
        return mesh;
    }

    private static void appendSphereQuads(VertexConsumer consumer, Matrix4f matrix, float[] vertices,
                                          int latSegments, int lonSegments, boolean stereo3d, boolean rightEye,
                                          float u1, float u2, int color) {
        int row = (lonSegments + 1) * 2;
        for (int latIndex = 0; latIndex < latSegments; latIndex++) {
            int first = latIndex * row;
            for (int lonIndex = 0; lonIndex < lonSegments; lonIndex++) {
                int top = first + lonIndex * 2;
                appendSphereVertex(consumer, matrix, vertices, top, stereo3d, rightEye, u1, u2, color);
                appendSphereVertex(consumer, matrix, vertices, top + 1, stereo3d, rightEye, u1, u2, color);
                appendSphereVertex(consumer, matrix, vertices, top + 3, stereo3d, rightEye, u1, u2, color);
                appendSphereVertex(consumer, matrix, vertices, top + 2, stereo3d, rightEye, u1, u2, color);
                appendSphereVertex(consumer, matrix, vertices, top + 2, stereo3d, rightEye, u1, u2, color);
                appendSphereVertex(consumer, matrix, vertices, top + 3, stereo3d, rightEye, u1, u2, color);
                appendSphereVertex(consumer, matrix, vertices, top + 1, stereo3d, rightEye, u1, u2, color);
                appendSphereVertex(consumer, matrix, vertices, top, stereo3d, rightEye, u1, u2, color);
            }
        }
    }

    private static void appendSphereVertex(VertexConsumer consumer, Matrix4f matrix, float[] vertices, int vertex,
                                           boolean stereo3d, boolean rightEye, float u1, float u2, int color) {
        int idx = vertex * 5;
        float u = vertices[idx + 3];
        if (stereo3d) {
            float split = (u1 + u2) * 0.5f;
            u = rightEye ? split + (u - u1) * 0.5f : u1 + (u - u1) * 0.5f;
        }
        Vector3f vertexPosition = new Vector3f(vertices[idx], vertices[idx + 1], vertices[idx + 2]);
        Vector3f normal = new Vector3f(vertexPosition);
        if (normal.lengthSquared() == 0.0f) normal.set(0.0f, 1.0f, 0.0f);
        else normal.normalize();
        ScreenRenderer.drawWorldTexturedVertex(matrix, consumer, vertexPosition, u, vertices[idx + 4], color, normal);
    }

    private static void applySphereRotation(PoseStack matrices, float x, float y, float z) {
        if (y != 0) matrices.mulPose(tmp.rotationY((float) Math.toRadians(y)));
        if (x != 0) matrices.mulPose(tmp.rotationX((float) Math.toRadians(x)));
        if (z != 0) matrices.mulPose(tmp.rotationZ((float) Math.toRadians(z)));
    }

    static float[] genVertices(float radius, int latSegments, int lonSegments, float us, float ue, float vs, float ve) {
        latSegments = clampSegments(latSegments);
        lonSegments = clampSegments(lonSegments);
        return genVertices(radius, latSegments, lonSegments, us, ue, vs, ve, 0.0, Math.PI * 2.0);
    }

    static float[] genHemisphereVertices(float radius, int latSegments, int lonSegments, float us, float ue, float vs, float ve) {
        latSegments = clampSegments(latSegments);
        lonSegments = clampSegments(lonSegments);
        return genVertices(radius, latSegments, lonSegments, us, ue, vs, ve, 0.0, Math.PI);
    }

    private static int clampSegments(int value) {
        return VideoScreen.clampSphereSegments(value);
    }

    private static float[] genVertices(float radius, int latSegments, int lonSegments, float us, float ue, float vs, float ve,
                                       double phiStart, double phiEnd) {
        int vertexCount = latSegments * (lonSegments + 1) * 2;
        float[] data = new float[vertexCount * 5];

        int idx = 0;
        double phiRange = phiEnd - phiStart;
        for (int lat = 0; lat < latSegments; lat++) {
            double theta1 = Math.PI * lat / latSegments;
            double theta2 = Math.PI * (lat + 1) / latSegments;
            for (int lon = 0; lon <= lonSegments; lon++) {
                double phi = phiStart + phiRange * lon / lonSegments;
                float y1 = (float) (radius * Math.cos(theta1));
                float y2 = (float) (radius * Math.cos(theta2));
                float r1 = (float) (radius * Math.sin(theta1));
                float r2 = (float) (radius * Math.sin(theta2));
                float x1 = (float) (r1 * Math.cos(phi));
                float x2 = (float) (r2 * Math.cos(phi));
                float z1 = (float) (r1 * Math.sin(phi));
                float z2 = (float) (r2 * Math.sin(phi));
                float u = Mth.lerp((float) lon / lonSegments, us, ue);
                float v1 = Mth.lerp((float) lat / latSegments, vs, ve);
                float v2 = Mth.lerp((float) (lat + 1) / latSegments, vs, ve);
                data[idx++] = x1;
                data[idx++] = y1;
                data[idx++] = z1;
                data[idx++] = u;
                data[idx++] = v1;
                data[idx++] = x2;
                data[idx++] = y2;
                data[idx++] = z2;
                data[idx++] = u;
                data[idx++] = v2;
            }
        }

        return data;
    }

    private record MeshKey(int radiusBits, int lat, int lon, int u1Bits, int u2Bits, int v1Bits, int v2Bits,
                           boolean hemisphere, boolean stereo3d, boolean rightEye) {
        private static MeshKey of(ClientVideoScreen screen, boolean stereo3d, boolean rightEye, int lat, int lon) {
            return new MeshKey(
                    Float.floatToIntBits(screen.sphereRadius),
                    lat,
                    lon,
                    Float.floatToIntBits(screen.u1),
                    Float.floatToIntBits(screen.u2),
                    Float.floatToIntBits(screen.v1),
                    Float.floatToIntBits(screen.v2),
                    stereo3d,
                    stereo3d,
                    rightEye
            );
        }

        private float radius() {
            return Float.intBitsToFloat(radiusBits);
        }

        private float u1() {
            return Float.intBitsToFloat(u1Bits);
        }

        private float u2() {
            return Float.intBitsToFloat(u2Bits);
        }

        private float v1() {
            return Float.intBitsToFloat(v1Bits);
        }

        private float v2() {
            return Float.intBitsToFloat(v2Bits);
        }
    }

}
