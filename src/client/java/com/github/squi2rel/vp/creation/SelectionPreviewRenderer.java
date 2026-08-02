package com.github.squi2rel.vp.creation;

import com.github.squi2rel.vp.ScreenRenderer;
import com.github.squi2rel.vp.VideoPlayerClient;
import com.github.squi2rel.vp.i18n.VpTexts;
import com.github.squi2rel.vp.video.ClientVideoArea;
import com.github.squi2rel.vp.video.ScreenGeometry;
import com.github.squi2rel.vp.video.VideoScreen;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SelectionPreviewRenderer {
    private static final int SCREEN_COLOR = 0xFF50A0FF;
    private static final int PREVIEW_COLOR = 0xFFFFD050;
    private static final int POINT_COLOR = 0xFFFF7050;
    private static final int SELECTED_POINT_COLOR = 0xFFFFFFFF;
    private static final int AXIS_X_COLOR = 0xFFFF4040;
    private static final int AXIS_Y_COLOR = 0xFF45E06F;
    private static final int AXIS_Z_COLOR = 0xFF5090FF;
    private static final int AXIS_HOT_COLOR = 0xFFFFFF80;
    private static final int PREVIEW_ALPHA = 120;

    private SelectionPreviewRenderer() {
    }

    public static void renderWorld(WorldRenderContext ctx) {
        VideoCreationEditor editor = VideoCreationEditor.instance();
        if (!editor.active()) return;
        VertexConsumerProvider consumers = ctx.consumers();

        MatrixStack matrices = ctx.matrices();
        if (matrices == null) return;

        Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
        matrices.push();

        drawScreenPreviewTexture(editor, matrices, consumers, camera);

        VertexConsumer consumer = consumers.getBuffer(RenderLayers.lines());
        drawExistingAreas(matrices, consumer, camera);
        drawExistingScreens(editor, matrices, consumer, camera);
        drawAreaPreview(editor, matrices, consumer, camera);
        drawScreenPreview(editor, matrices, consumer, camera);
        drawSelectionPoints(editor, matrices, consumer, camera);
        drawGizmo(editor, matrices, consumer, camera);

        matrices.pop();
    }

    public static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        VideoCreationEditor editor = VideoCreationEditor.instance();
        if (!editor.selecting()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        int x = context.getScaledWindowWidth() / 2 + 12;
        int y = context.getScaledWindowHeight() / 2 + 12;
        int color = editor.statusError() ? 0xFFFF5555 : 0xFFFFFFFF;
        context.drawTextWithShadow(client.textRenderer, editor.modeText(), x, y, 0xFFFFD050);
        context.drawTextWithShadow(client.textRenderer, VpTexts.tr("label.videoplayer.point_progress", "Points %s", editor.pointProgress()), x, y + 11, 0xFFE0E0E0);
        context.drawTextWithShadow(client.textRenderer, editor.status(), x, y + 22, color);
        VideoCreationEditor.SelectionPoint selected = editor.selectedPoint();
        if (editor.screenGizmoVisible() && selected != null) {
            context.drawTextWithShadow(client.textRenderer, VpTexts.tr("label.videoplayer.selected_point", "Selected %s: %s", editor.selectedPointIndex() + 1, selected.format()), x, y + 33, 0xFFB0B0B0);
        } else if (editor.showCurrentTargetPoint()) {
            VideoCreationEditor.SelectionPoint target = editor.currentTargetPoint();
            if (target == null) return;
            context.drawTextWithShadow(client.textRenderer, Text.literal(target.format()), x, y + 33, 0xFFB0B0B0);
        }
    }

    private static void drawExistingAreas(MatrixStack matrices, VertexConsumer consumer, Vec3d camera) {
        for (ClientVideoArea area : VideoPlayerClient.areas.values()) {
            drawBox(
                    matrices,
                    consumer,
                    area.min.x, area.min.y, area.min.z,
                    area.max.x, area.max.y, area.max.z,
                    0xB32ED180,
                    camera
            );
        }
    }

    private static void drawExistingScreens(VideoCreationEditor editor, MatrixStack matrices, VertexConsumer consumer, Vec3d camera) {
        for (ClientVideoArea area : VideoPlayerClient.areas.values()) {
            for (VideoScreen screen : area.screens) {
                if (screen.vertices != null && screen.vertices.size() >= ScreenGeometry.MIN_VERTICES) {
                    drawPolygon(matrices, consumer, screen.vertices, SCREEN_COLOR, camera);
                }
                if (editor.selectingSpherePreset() && screen.spherePreset) {
                    drawSphere(matrices, consumer, screen.sphereCenter, screen.sphereRadius, SCREEN_COLOR, screen.stereo3d, camera);
                }
            }
        }
    }

    private static void drawAreaPreview(VideoCreationEditor editor, MatrixStack matrices, VertexConsumer consumer, Vec3d camera) {
        Box box = editor.areaPreview();
        if (box == null) return;
        drawBox(
                matrices,
                consumer,
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                PREVIEW_COLOR,
                camera
        );
    }

    private static void drawScreenPreviewTexture(VideoCreationEditor editor, MatrixStack matrices, VertexConsumerProvider consumers, Vec3d camera) {
        if (editor.selectingSpherePreset()) return;
        List<Vector3f> vertices = editor.previewVertices();
        if (vertices != null) {
            drawPlaceholderPreview(matrices, consumers, vertices, camera);
        }
    }

    private static void drawScreenPreview(VideoCreationEditor editor, MatrixStack matrices, VertexConsumer consumer, Vec3d camera) {
        Vector3f center = editor.spherePreviewCenter();
        float radius = editor.spherePreviewRadius();
        if (center != null && radius > ScreenGeometry.EPSILON) {
            drawSphere(matrices, consumer, center, radius, PREVIEW_COLOR, editor.draft().stereo3d, camera);
        }
        if (editor.selectingSpherePreset()) {
            return;
        }
        List<Vector3f> vertices = editor.previewVertices();
        if (vertices != null) {
            drawPolygon(matrices, consumer, vertices, PREVIEW_COLOR, camera);
            return;
        }
        if (editor.draft().target != VideoCreationEditor.Target.SCREEN) return;
        if (editor.points().isEmpty()) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        for (int i = 1; i < editor.points().size(); i++) {
            drawWorldLine(matrix, consumer, editor.points().get(i - 1).point, editor.points().get(i).point, PREVIEW_COLOR, camera);
        }
        VideoCreationEditor.SelectionPoint current = editor.showCurrentTargetPoint() ? editor.currentTargetPoint() : null;
        if (current != null) {
            drawWorldLine(matrix, consumer, editor.points().getLast().point, current.point, PREVIEW_COLOR, camera);
        }
    }

    private static void drawSelectionPoints(VideoCreationEditor editor, MatrixStack matrices, VertexConsumer consumer, Vec3d camera) {
        for (int i = 0; i < editor.points().size(); i++) {
            VideoCreationEditor.SelectionPoint point = editor.points().get(i);
            drawPoint(matrices, consumer, point.point, i == editor.selectedPointIndex() ? SELECTED_POINT_COLOR : POINT_COLOR, camera);
        }
        if (editor.selecting() && editor.showCurrentTargetPoint()) {
            VideoCreationEditor.SelectionPoint current = editor.currentTargetPoint();
            if (current != null) drawPoint(matrices, consumer, current.point, PREVIEW_COLOR, camera);
        }
    }

    private static void drawGizmo(VideoCreationEditor editor, MatrixStack matrices, VertexConsumer consumer, Vec3d camera) {
        if (!editor.screenGizmoVisible()) return;
        VideoCreationEditor.SelectionPoint selected = editor.selectedPoint();
        if (selected == null) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        for (VideoCreationEditor.GizmoAxis axis : VideoCreationEditor.GizmoAxis.values()) {
            int color = axisColor(editor, axis);
            drawAxis(matrix, consumer, selected.point, axis, editor.gizmoStart(), editor.gizmoLength(), color, camera);
        }
    }

    private static int axisColor(VideoCreationEditor editor, VideoCreationEditor.GizmoAxis axis) {
        if (axis == editor.draggingAxis() || axis == editor.hoveredAxis()) return AXIS_HOT_COLOR;
        return switch (axis) {
            case X -> AXIS_X_COLOR;
            case Y -> AXIS_Y_COLOR;
            case Z -> AXIS_Z_COLOR;
        };
    }

    private static void drawAxis(Matrix4f matrix, VertexConsumer consumer, Vector3f origin,
                                 VideoCreationEditor.GizmoAxis axis, float startDistance, float length, int color, Vec3d camera) {
        Vector3f axisVector = axis.vector();
        Vector3f start = new Vector3f(origin).add(new Vector3f(axisVector).mul(startDistance));
        Vector3f end = new Vector3f(origin).add(new Vector3f(axisVector).mul(length));
        drawWorldLine(matrix, consumer, start, end, color, camera);

        float headLength = 0.12f;
        float headWidth = 0.045f;
        Vector3f base = new Vector3f(end).sub(new Vector3f(axisVector).mul(headLength));
        Vector3f sideA = arrowSide(axis, true).mul(headWidth);
        Vector3f sideB = arrowSide(axis, false).mul(headWidth);
        drawWorldLine(matrix, consumer, end, new Vector3f(base).add(sideA), color, camera);
        drawWorldLine(matrix, consumer, end, new Vector3f(base).sub(sideA), color, camera);
        drawWorldLine(matrix, consumer, end, new Vector3f(base).add(sideB), color, camera);
        drawWorldLine(matrix, consumer, end, new Vector3f(base).sub(sideB), color, camera);
    }

    private static Vector3f arrowSide(VideoCreationEditor.GizmoAxis axis, boolean first) {
        return switch (axis) {
            case X -> first ? new Vector3f(0, 1, 0) : new Vector3f(0, 0, 1);
            case Y -> first ? new Vector3f(1, 0, 0) : new Vector3f(0, 0, 1);
            case Z -> first ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        };
    }

    private static void drawPoint(MatrixStack matrices, VertexConsumer consumer, Vector3f point, int color, Vec3d camera) {
        float size = 0.045f;
        drawBox(
                matrices,
                consumer,
                point.x - size, point.y - size, point.z - size,
                point.x + size, point.y + size, point.z + size,
                color,
                camera
        );
    }

    private static void drawBox(MatrixStack matrices, VertexConsumer consumer,
                                double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ,
                                int color, Vec3d camera) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float relativeMinX = (float) (minX - camera.x);
        float relativeMinY = (float) (minY - camera.y);
        float relativeMinZ = (float) (minZ - camera.z);
        float relativeMaxX = (float) (maxX - camera.x);
        float relativeMaxY = (float) (maxY - camera.y);
        float relativeMaxZ = (float) (maxZ - camera.z);
        Vector3f p000 = new Vector3f(relativeMinX, relativeMinY, relativeMinZ);
        Vector3f p001 = new Vector3f(relativeMinX, relativeMinY, relativeMaxZ);
        Vector3f p010 = new Vector3f(relativeMinX, relativeMaxY, relativeMinZ);
        Vector3f p011 = new Vector3f(relativeMinX, relativeMaxY, relativeMaxZ);
        Vector3f p100 = new Vector3f(relativeMaxX, relativeMinY, relativeMinZ);
        Vector3f p101 = new Vector3f(relativeMaxX, relativeMinY, relativeMaxZ);
        Vector3f p110 = new Vector3f(relativeMaxX, relativeMaxY, relativeMinZ);
        Vector3f p111 = new Vector3f(relativeMaxX, relativeMaxY, relativeMaxZ);
        drawLine(matrix, consumer, p000, p001, color);
        drawLine(matrix, consumer, p001, p101, color);
        drawLine(matrix, consumer, p101, p100, color);
        drawLine(matrix, consumer, p100, p000, color);
        drawLine(matrix, consumer, p010, p011, color);
        drawLine(matrix, consumer, p011, p111, color);
        drawLine(matrix, consumer, p111, p110, color);
        drawLine(matrix, consumer, p110, p010, color);
        drawLine(matrix, consumer, p000, p010, color);
        drawLine(matrix, consumer, p001, p011, color);
        drawLine(matrix, consumer, p101, p111, color);
        drawLine(matrix, consumer, p100, p110, color);
    }

    private static void drawPolygon(MatrixStack matrices, VertexConsumer consumer, List<Vector3f> vertices, int color, Vec3d camera) {
        if (vertices == null || vertices.size() < 2) return;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        for (int i = 0; i < vertices.size(); i++) {
            drawWorldLine(matrix, consumer, vertices.get(i), vertices.get((i + 1) % vertices.size()), color, camera);
        }
        try {
            ScreenGeometry geometry = ScreenGeometry.create(vertices);
            Vector3f relativeOrigin = geometry.relativeOrigin(camera.x, camera.y, camera.z);
            matrices.push();
            matrices.translate(relativeOrigin.x, relativeOrigin.y, relativeOrigin.z);
            drawTriangleEdges(matrices.peek().getPositionMatrix(), consumer, geometry.localVertices(), geometry.triangles(), color);
            matrices.pop();
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static void drawTriangleEdges(Matrix4f matrix, VertexConsumer consumer, List<Vector3f> vertices, int[] triangles, int color) {
        Set<Long> drawn = new HashSet<>();
        for (int i = 0; i < triangles.length; i += 3) {
            drawTriangleEdge(matrix, consumer, vertices, triangles[i], triangles[i + 1], color, drawn);
            drawTriangleEdge(matrix, consumer, vertices, triangles[i + 1], triangles[i + 2], color, drawn);
            drawTriangleEdge(matrix, consumer, vertices, triangles[i + 2], triangles[i], color, drawn);
        }
    }

    private static void drawTriangleEdge(Matrix4f matrix, VertexConsumer consumer, List<Vector3f> vertices,
                                         int from, int to, int color, Set<Long> drawn) {
        int size = vertices.size();
        int diff = Math.abs(from - to);
        if (diff == 1 || diff == size - 1) return;
        int min = Math.min(from, to);
        int max = Math.max(from, to);
        long key = ((long) min << 32) | max;
        if (!drawn.add(key)) return;
        drawLine(matrix, consumer, vertices.get(from), vertices.get(to), color);
    }

    private static void drawLine(Matrix4f matrix, VertexConsumer consumer, Vector3f from, Vector3f to, int color) {
        Vector3f normal = new Vector3f(to).sub(from);
        if (normal.lengthSquared() == 0) return;
        normal.normalize();
        consumer.vertex(matrix, from.x, from.y, from.z).color(color).normal(normal.x, normal.y, normal.z).lineWidth(1.0f);
        consumer.vertex(matrix, to.x, to.y, to.z).color(color).normal(normal.x, normal.y, normal.z).lineWidth(1.0f);
    }

    private static void drawWorldLine(Matrix4f matrix, VertexConsumer consumer, Vector3f from, Vector3f to, int color, Vec3d camera) {
        drawLine(matrix, consumer, relative(from, camera), relative(to, camera), color);
    }

    private static Vector3f relative(Vector3f point, Vec3d camera) {
        return new Vector3f(
                (float) (point.x - camera.x),
                (float) (point.y - camera.y),
                (float) (point.z - camera.z)
        );
    }

    private static void drawSphere(MatrixStack matrices, VertexConsumer consumer, Vector3f center, float radius, int color, boolean hemisphere, Vec3d camera) {
        if (center == null || !Float.isFinite(radius) || radius <= 0) return;
        Vector3f relativeCenter = relative(center, camera);
        matrices.push();
        matrices.translate(relativeCenter.x, relativeCenter.y, relativeCenter.z);
        if (hemisphere) {
            drawHemisphere(matrices, consumer, radius, color);
            matrices.pop();
            return;
        }
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            float a = (float) (Math.PI * 2 * i / segments);
            float b = (float) (Math.PI * 2 * (i + 1) / segments);
            drawLine(matrix, consumer,
                    new Vector3f((float) Math.cos(a) * radius, 0, (float) Math.sin(a) * radius),
                    new Vector3f((float) Math.cos(b) * radius, 0, (float) Math.sin(b) * radius),
                    color);
            drawLine(matrix, consumer,
                    new Vector3f((float) Math.cos(a) * radius, (float) Math.sin(a) * radius, 0),
                    new Vector3f((float) Math.cos(b) * radius, (float) Math.sin(b) * radius, 0),
                    color);
            drawLine(matrix, consumer,
                    new Vector3f(0, (float) Math.cos(a) * radius, (float) Math.sin(a) * radius),
                    new Vector3f(0, (float) Math.cos(b) * radius, (float) Math.sin(b) * radius),
                    color);
        }
        matrices.pop();
    }

    private static void drawHemisphere(MatrixStack matrices, VertexConsumer consumer, float radius, int color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            float a = (float) (Math.PI * i / segments);
            float b = (float) (Math.PI * (i + 1) / segments);
            drawLine(matrix, consumer,
                    new Vector3f((float) Math.cos(a) * radius, 0, (float) Math.sin(a) * radius),
                    new Vector3f((float) Math.cos(b) * radius, 0, (float) Math.sin(b) * radius),
                    color);
            drawLine(matrix, consumer,
                    new Vector3f(0, (float) Math.cos(a) * radius, (float) Math.sin(a) * radius),
                    new Vector3f(0, (float) Math.cos(b) * radius, (float) Math.sin(b) * radius),
                    color);

            float rimA = (float) (Math.PI * 2 * i / segments);
            float rimB = (float) (Math.PI * 2 * (i + 1) / segments);
            drawLine(matrix, consumer,
                    new Vector3f((float) Math.cos(rimA) * radius, (float) Math.sin(rimA) * radius, 0),
                    new Vector3f((float) Math.cos(rimB) * radius, (float) Math.sin(rimB) * radius, 0),
                    color);
        }
    }

    private static void drawPlaceholderPreview(MatrixStack matrices, VertexConsumerProvider consumers, List<Vector3f> vertices, Vec3d camera) {
        if (vertices == null || vertices.size() < ScreenGeometry.MIN_VERTICES) return;
        ScreenGeometry geometry;
        try {
            geometry = ScreenGeometry.create(vertices);
        } catch (IllegalArgumentException ignored) {
            return;
        }

        int previewTextureId = ScreenRenderer.placeholderTextureId();
        Vector3f relativeOrigin = geometry.relativeOrigin(camera.x, camera.y, camera.z);
        matrices.push();
        matrices.translate(relativeOrigin.x, relativeOrigin.y, relativeOrigin.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float[] bounds = geometry.contentBounds(0, 0, 1, 1, false, 1, 1, 960, 540);
        int[] triangles = geometry.triangles();
        List<Vector3f> geometryVertices = geometry.localVertices();
        Vector3f normal = geometry.normal();
        VertexConsumer backingConsumer = consumers.getBuffer(ScreenRenderer.getBackingLayer(previewTextureId));
        for (int i = 0; i < triangles.length; i += 3) {
            drawPreviewTriangle(matrix, backingConsumer, geometry, geometryVertices, triangles, i, bounds, normal, PREVIEW_ALPHA << 24);
        }

        RenderLayer layer = ScreenRenderer.getTranslucentLayer(previewTextureId);
        VertexConsumer textureConsumer = consumers.getBuffer(layer);
        for (int i = 0; i < triangles.length; i += 3) {
            drawPreviewTriangle(matrix, textureConsumer, geometry, geometryVertices, triangles, i, bounds, normal, (PREVIEW_ALPHA << 24) | 0x00FFFFFF);
        }
        matrices.pop();
    }

    private static void drawPreviewTriangle(Matrix4f matrix, VertexConsumer consumer, ScreenGeometry geometry,
                                            List<Vector3f> vertices, int[] triangles, int offset, float[] bounds, Vector3f normal,
                                            int color) {
        ArrayList<ProjectedVertex> polygon = new ArrayList<>(3);
        addProjectedVertex(polygon, geometry, vertices, triangles[offset]);
        addProjectedVertex(polygon, geometry, vertices, triangles[offset + 1]);
        addProjectedVertex(polygon, geometry, vertices, triangles[offset + 2]);
        polygon = clipPolygon(polygon, bounds);
        if (polygon.size() < 3) return;

        ProjectedVertex first = polygon.getFirst();
        for (int i = 1; i < polygon.size() - 1; i++) {
            drawPreviewVertex(matrix, consumer, geometry, first, bounds, normal, color);
            drawPreviewVertex(matrix, consumer, geometry, polygon.get(i), bounds, normal, color);
            drawPreviewVertex(matrix, consumer, geometry, polygon.get(i + 1), bounds, normal, color);
            drawPreviewVertex(matrix, consumer, geometry, polygon.get(i + 1), bounds, normal, color);
            drawPreviewVertex(matrix, consumer, geometry, first, bounds, normal, color);
            drawPreviewVertex(matrix, consumer, geometry, polygon.get(i + 1), bounds, normal, color);
            drawPreviewVertex(matrix, consumer, geometry, polygon.get(i), bounds, normal, color);
            drawPreviewVertex(matrix, consumer, geometry, polygon.get(i), bounds, normal, color);
        }
    }

    private static void addProjectedVertex(ArrayList<ProjectedVertex> polygon, ScreenGeometry geometry, List<Vector3f> vertices, int index) {
        polygon.add(new ProjectedVertex(geometry.projectedPoint(index), geometry.editPoint(index), new Vector3f(vertices.get(index))));
    }

    private static void drawPreviewVertex(Matrix4f matrix, VertexConsumer consumer, ScreenGeometry geometry,
                                          ProjectedVertex projected, float[] bounds, Vector3f normal, int color) {
        Vector2f uv = geometry.textureCoord(projected.texturePoint.x, projected.texturePoint.y, bounds, 0, 0, 1, 1);
        Vector3f vertex = projected.vertex;
        ScreenRenderer.drawWorldTexturedVertex(matrix, consumer, vertex, uv.x, uv.y, color, normal);
    }

    private static ArrayList<ProjectedVertex> clipPolygon(ArrayList<ProjectedVertex> polygon, float[] bounds) {
        polygon = clip(polygon, bounds[0], true, true);
        polygon = clip(polygon, bounds[1], true, false);
        polygon = clip(polygon, bounds[2], false, true);
        polygon = clip(polygon, bounds[3], false, false);
        return polygon;
    }

    private static ArrayList<ProjectedVertex> clip(ArrayList<ProjectedVertex> input, float limit, boolean axisU, boolean keepGreater) {
        ArrayList<ProjectedVertex> output = new ArrayList<>();
        if (input.isEmpty()) return output;

        ProjectedVertex previous = input.getLast();
        boolean previousInside = inside(previous, limit, axisU, keepGreater);
        for (ProjectedVertex current : input) {
            boolean currentInside = inside(current, limit, axisU, keepGreater);
            if (currentInside != previousInside) {
                output.add(intersection(previous, current, limit, axisU));
            }
            if (currentInside) {
                output.add(current.copy());
            }
            previous = current;
            previousInside = currentInside;
        }
        return output;
    }

    private static boolean inside(ProjectedVertex projected, float limit, boolean axisU, boolean keepGreater) {
        float value = axisU ? projected.texturePoint.x : projected.texturePoint.y;
        return keepGreater ? value >= limit - ScreenGeometry.EPSILON : value <= limit + ScreenGeometry.EPSILON;
    }

    private static ProjectedVertex intersection(ProjectedVertex from, ProjectedVertex to, float limit, boolean axisU) {
        float start = axisU ? from.texturePoint.x : from.texturePoint.y;
        float end = axisU ? to.texturePoint.x : to.texturePoint.y;
        float delta = end - start;
        if (Math.abs(delta) < ScreenGeometry.EPSILON) return to.copy();
        float t = (limit - start) / delta;
        return new ProjectedVertex(
                new Vector2f(
                        from.point.x + (to.point.x - from.point.x) * t,
                        from.point.y + (to.point.y - from.point.y) * t
                ),
                new Vector2f(
                        from.texturePoint.x + (to.texturePoint.x - from.texturePoint.x) * t,
                        from.texturePoint.y + (to.texturePoint.y - from.texturePoint.y) * t
                ),
                new Vector3f(
                        from.vertex.x + (to.vertex.x - from.vertex.x) * t,
                        from.vertex.y + (to.vertex.y - from.vertex.y) * t,
                        from.vertex.z + (to.vertex.z - from.vertex.z) * t
                )
        );
    }

    private record ProjectedVertex(Vector2f point, Vector2f texturePoint, Vector3f vertex) {
        private ProjectedVertex copy() {
            return new ProjectedVertex(new Vector2f(point), new Vector2f(texturePoint), new Vector3f(vertex));
        }
    }

}
