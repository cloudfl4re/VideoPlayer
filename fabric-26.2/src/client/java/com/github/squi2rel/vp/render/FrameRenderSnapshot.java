package com.github.squi2rel.vp.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class FrameRenderSnapshot {
    public static final FrameRenderSnapshot EMPTY = new FrameRenderSnapshot(List.of());

    private final List<Command> commands;

    public FrameRenderSnapshot(List<Command> commands) {
        this.commands = List.copyOf(commands);
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    public void submit(SubmitNodeCollector collector) {
        for (int i = 0; i < commands.size(); i++) {
            Command command = commands.get(i);
            collector.order(i).submitCustomGeometry(new PoseStack(), command.renderType(),
                    (pose, consumer) -> emit(command.geometry(), pose, consumer));
        }
    }

    private static void emit(FrameRenderGeometry geometry, PoseStack.Pose pose, VertexConsumer consumer) {
        for (int vertex = 0; vertex < geometry.vertexCount(); vertex++) {
            consumer.addVertex(pose, geometry.x(vertex), geometry.y(vertex), geometry.z(vertex))
                    .setColor(geometry.color(vertex));
            if (geometry.has(vertex, FrameRenderGeometry.UV)) {
                consumer.setUv(geometry.u(vertex), geometry.v(vertex));
            }
            if (geometry.has(vertex, FrameRenderGeometry.OVERLAY)) {
                consumer.setOverlay(geometry.overlay(vertex));
            }
            if (geometry.has(vertex, FrameRenderGeometry.LIGHT)) {
                consumer.setLight(geometry.light(vertex));
            }
            if (geometry.has(vertex, FrameRenderGeometry.NORMAL)) {
                consumer.setNormal(pose, geometry.normalX(vertex), geometry.normalY(vertex), geometry.normalZ(vertex));
            }
            if (geometry.has(vertex, FrameRenderGeometry.LINE_WIDTH)) {
                consumer.setLineWidth(geometry.lineWidth(vertex));
            }
        }
    }

    public record Command(RenderType renderType, FrameRenderGeometry geometry) {
        public Command {
            Objects.requireNonNull(renderType, "renderType");
            Objects.requireNonNull(geometry, "geometry");
        }
    }
}
