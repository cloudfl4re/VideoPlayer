package com.github.squi2rel.vp.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class WorldRenderBatch {
    private final Map<RenderType, CapturingVertexConsumer> consumers = new LinkedHashMap<>();

    public VertexConsumer getBuffer(RenderType renderType) {
        return consumers.computeIfAbsent(renderType, ignored -> new CapturingVertexConsumer());
    }

    public FrameRenderSnapshot snapshot() {
        ArrayList<FrameRenderSnapshot.Command> commands = new ArrayList<>(consumers.size());
        for (Map.Entry<RenderType, CapturingVertexConsumer> entry : consumers.entrySet()) {
            FrameRenderGeometry geometry = entry.getValue().geometry();
            if (geometry.vertexCount() > 0) {
                commands.add(new FrameRenderSnapshot.Command(entry.getKey(), geometry));
            }
        }
        return new FrameRenderSnapshot(commands);
    }

    private static final class CapturingVertexConsumer implements VertexConsumer {
        private final List<MutableVertex> vertices = new ArrayList<>();
        private MutableVertex current;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            finishCurrent();
            current = new MutableVertex();
            current.x = x;
            current.y = y;
            current.z = z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            if (current != null) {
                current.color = (Math.clamp(alpha, 0, 255) << 24)
                        | (Math.clamp(red, 0, 255) << 16)
                        | (Math.clamp(green, 0, 255) << 8)
                        | Math.clamp(blue, 0, 255);
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            if (current != null) current.color = color;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (current != null) {
                current.u = u;
                current.v = v;
                current.attributes |= FrameRenderGeometry.UV;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            if (current != null) {
                current.overlay = (v << 16) | (u & 0xFFFF);
                current.attributes |= FrameRenderGeometry.OVERLAY;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (current != null) {
                current.light = (v << 16) | (u & 0xFFFF);
                current.attributes |= FrameRenderGeometry.LIGHT;
            }
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (current != null) {
                current.normalX = x;
                current.normalY = y;
                current.normalZ = z;
                current.attributes |= FrameRenderGeometry.NORMAL;
            }
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            if (current != null) {
                current.lineWidth = width;
                current.attributes |= FrameRenderGeometry.LINE_WIDTH;
            }
            return this;
        }

        private FrameRenderGeometry geometry() {
            finishCurrent();
            int count = vertices.size();
            float[] positions = new float[count * 3];
            float[] uvs = new float[count * 2];
            float[] normals = new float[count * 3];
            float[] lineWidths = new float[count];
            int[] colors = new int[count];
            int[] lights = new int[count];
            int[] overlays = new int[count];
            int[] attributes = new int[count];
            for (int i = 0; i < count; i++) {
                MutableVertex vertex = vertices.get(i);
                positions[i * 3] = vertex.x;
                positions[i * 3 + 1] = vertex.y;
                positions[i * 3 + 2] = vertex.z;
                uvs[i * 2] = vertex.u;
                uvs[i * 2 + 1] = vertex.v;
                normals[i * 3] = vertex.normalX;
                normals[i * 3 + 1] = vertex.normalY;
                normals[i * 3 + 2] = vertex.normalZ;
                lineWidths[i] = vertex.lineWidth;
                colors[i] = vertex.color;
                lights[i] = vertex.light;
                overlays[i] = vertex.overlay;
                attributes[i] = vertex.attributes;
            }
            return new FrameRenderGeometry(positions, uvs, normals, lineWidths, colors, lights, overlays, attributes);
        }

        private void finishCurrent() {
            if (current == null) return;
            vertices.add(current);
            current = null;
        }
    }

    private static final class MutableVertex {
        private float x;
        private float y;
        private float z;
        private float u;
        private float v;
        private float normalX;
        private float normalY = 1.0f;
        private float normalZ;
        private float lineWidth = 1.0f;
        private int color = 0xFFFFFFFF;
        private int light;
        private int overlay;
        private int attributes;
    }
}
