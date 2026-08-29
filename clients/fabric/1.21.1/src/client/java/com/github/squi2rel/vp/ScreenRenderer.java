package com.github.squi2rel.vp;

import com.github.squi2rel.vp.danmaku.ClientDanmakuRenderer;
import com.github.squi2rel.vp.render.ExternalTextureRegistry;
import com.github.squi2rel.vp.video.ClientVideoScreen;
import com.github.squi2rel.vp.video.ExternalGlTexture;
import com.github.squi2rel.vp.vivecraft.Vivecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

import static com.github.squi2rel.vp.VideoPlayerClient.screens;

@SuppressWarnings({"resource", "DataFlowIssue"})
public final class ScreenRenderer {
    private static final ResourceLocation PLACEHOLDER_TEXTURE = ResourceLocation.fromNamespaceAndPath("videoplayer", "placeholder.png");
    private static final ExternalTextureRegistry EXTERNAL_TEXTURES = new ExternalTextureRegistry();
    private static final Quaternionf rotation = new Quaternionf();

    public static float cameraX;
    public static float cameraY;
    public static float cameraZ;
    public static double preciseCameraX;
    public static double preciseCameraY;
    public static double preciseCameraZ;
    public static boolean skybox;

    private ScreenRenderer() {
    }

    public static void render(WorldRenderContext context) {
        if (CameraRenderer.rendering) return;
        skybox = false;
        PoseStack matrices = context.matrixStack();
        Camera cameraObject = context.camera();
        Vec3 camera = cameraObject.getPosition();
        preciseCameraX = camera.x;
        preciseCameraY = camera.y;
        preciseCameraZ = camera.z;
        cameraX = (float) camera.x;
        cameraY = (float) camera.y;
        cameraZ = (float) camera.z;
        if (Vivecraft.loaded && Vivecraft.isVRActive()) {
            rotation.setFromNormalized(Vivecraft.getRotation()).invert();
        } else {
            cameraObject.rotation().invert(rotation);
        }

        MultiBufferSource consumers = context.consumers();
        ClientDanmakuRenderer.beginFrame(screens);
        matrices.pushPose();
        for (ClientVideoScreen screen : List.copyOf(screens)) {
            try {
                screen.draw(matrices, consumers);
            } catch (RuntimeException error) {
                VideoPlayerMain.LOGGER.error("Exception while rendering video screen", error);
            }
        }
        if (consumers instanceof MultiBufferSource.BufferSource immediate) {
            immediate.endBatch();
        }
        matrices.popPose();
    }

    public static RenderType getLayer(int textureId) {
        return getLayer(textureIdentifier(textureId));
    }

    public static RenderType getLayer(ResourceLocation texture) {
        return UnlitVideoRenderType.get(texture);
    }

    public static RenderType getTranslucentLayer(int textureId) {
        return getLayer(textureId);
    }

    public static RenderType getTranslucentLayer(ResourceLocation texture) {
        return getLayer(texture);
    }

    public static RenderType getBackingLayer(int textureId) {
        return RenderType.entityTranslucent(textureIdentifier(textureId), false);
    }

    public static ResourceLocation textureIdentifier(int textureId) {
        if (textureId < 0) return PLACEHOLDER_TEXTURE;
        ExternalTextureRegistry.Acquisition acquisition = EXTERNAL_TEXTURES.acquire(textureId);
        ResourceLocation identifier = textureIdentifier(acquisition.registration());
        if (acquisition.created()) {
            Minecraft.getInstance().getTextureManager().register(identifier, new ExternalGlTexture(textureId));
        }
        return identifier;
    }

    public static void releaseTexture(int textureId) {
        if (textureId < 0) return;
        EXTERNAL_TEXTURES.release(textureId).ifPresent(ScreenRenderer::releaseTexture);
    }

    public static void clearExternalTextures() {
        List<ExternalTextureRegistry.Registration> registrations = EXTERNAL_TEXTURES.clear();
        runOnClientThread(() -> {
            UnlitVideoRenderType.clear();
            for (ExternalTextureRegistry.Registration registration : registrations) {
                Minecraft.getInstance().getTextureManager().release(textureIdentifier(registration));
            }
        });
    }

    private static void releaseTexture(ExternalTextureRegistry.Registration registration) {
        runOnClientThread(() -> {
            ResourceLocation identifier = textureIdentifier(registration);
            UnlitVideoRenderType.release(identifier);
            Minecraft.getInstance().getTextureManager().release(identifier);
        });
    }

    private static ResourceLocation textureIdentifier(ExternalTextureRegistry.Registration registration) {
        return ResourceLocation.fromNamespaceAndPath("videoplayer", registration.identifierPath());
    }

    private static void runOnClientThread(Runnable task) {
        Minecraft client = Minecraft.getInstance();
        if (client.isSameThread()) task.run();
        else client.execute(task);
    }

    public static int placeholderTextureId() {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(PLACEHOLDER_TEXTURE);
        return texture.getId();
    }

    public static void rotateMatrix(PoseStack matrices) {
        matrices.mulPose(rotation);
    }

    public static void drawWorldTexturedVertex(Matrix4f matrix, VertexConsumer consumer, Vector3f vertex,
                                               float u, float v, int color, Vector3f normal) {
        Vector3f safeNormal = normal == null ? new Vector3f(0.0f, 1.0f, 0.0f) : normal;
        consumer.addVertex(matrix, vertex.x, vertex.y, vertex.z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0x00F000F0)
                .setNormal(safeNormal.x, safeNormal.y, safeNormal.z);
    }

    public static void drawWorldTexturedVertex(Matrix4f matrix, VertexConsumer consumer,
                                               float x, float y, float z, float u, float v, int color,
                                               float nx, float ny, float nz) {
        drawWorldTexturedVertex(matrix, consumer, new Vector3f(x, y, z), u, v, color, new Vector3f(nx, ny, nz));
    }

    public static void drawGuiTexturedTriangles(GuiGraphics context, int textureId, List<GuiVertex> vertices) {
        if (vertices == null || vertices.size() < 3) return;
        context.flush();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, textureIdentifier(textureId));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        Matrix4f matrix = context.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
        int count = vertices.size() - vertices.size() % 3;
        for (int i = 0; i < count; i++) {
            GuiVertex vertex = vertices.get(i);
            buffer.addVertex(matrix, vertex.x, vertex.y, 0.0f).setUv(vertex.u, vertex.v).setColor(vertex.color);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public record GuiVertex(float x, float y, float u, float v, int color) {
    }
}
