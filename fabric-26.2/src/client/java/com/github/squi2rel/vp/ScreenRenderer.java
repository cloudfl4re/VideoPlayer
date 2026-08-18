package com.github.squi2rel.vp;

import com.github.squi2rel.vp.creation.SelectionPreviewRenderer;
import com.github.squi2rel.vp.danmaku.ClientDanmakuRenderer;
import com.github.squi2rel.vp.mixin.client.DrawContextAccessor;
import com.github.squi2rel.vp.render.ExternalTextureRegistry;
import com.github.squi2rel.vp.render.FrameRenderSnapshot;
import com.github.squi2rel.vp.render.WorldRenderBatch;
import com.github.squi2rel.vp.video.ClientVideoScreen;
import com.github.squi2rel.vp.video.ExternalGlTexture;
import com.github.squi2rel.vp.vivecraft.Vivecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import static com.github.squi2rel.vp.VideoPlayerClient.screens;

@SuppressWarnings({"resource", "DataFlowIssue"})
public final class ScreenRenderer {
    private static final Identifier PLACEHOLDER_TEXTURE = Identifier.fromNamespaceAndPath("videoplayer", "placeholder.png");
    private static final ExternalTextureRegistry EXTERNAL_TEXTURES = new ExternalTextureRegistry();
    private static final Quaternionf rotation = new Quaternionf();
    private static volatile FrameRenderSnapshot frameSnapshot = FrameRenderSnapshot.EMPTY;

    public static float cameraX;
    public static float cameraY;
    public static float cameraZ;
    public static double preciseCameraX;
    public static double preciseCameraY;
    public static double preciseCameraZ;
    public static boolean skybox;

    private ScreenRenderer() {
    }

    public static void extract(LevelExtractionContext context) {
        if (CameraRenderer.isRendering()) {
            frameSnapshot = FrameRenderSnapshot.EMPTY;
            return;
        }
        Camera cameraObject = context.camera();
        Vec3 camera = cameraObject.position();
        preciseCameraX = camera.x;
        preciseCameraY = camera.y;
        preciseCameraZ = camera.z;
        cameraX = (float) camera.x;
        cameraY = (float) camera.y;
        cameraZ = (float) camera.z;
        skybox = false;
        if (Vivecraft.loaded && Vivecraft.isVRActive()) {
            rotation.setFromNormalized(Vivecraft.getRotation()).invert();
        } else {
            cameraObject.rotation().invert(rotation);
        }

        WorldRenderBatch batch = new WorldRenderBatch();
        PoseStack matrices = new PoseStack();
        List<ClientVideoScreen> currentScreens = new ArrayList<>(screens);
        ClientDanmakuRenderer.beginFrame(currentScreens);
        for (ClientVideoScreen screen : currentScreens) {
            try {
                screen.draw(matrices, batch);
            } catch (RuntimeException error) {
                VideoPlayerMain.LOGGER.error("Exception while extracting video screen render state", error);
            }
        }
        SelectionPreviewRenderer.extractWorld(batch, cameraObject);
        frameSnapshot = batch.snapshot();
    }

    public static void submit(LevelRenderContext context) {
        if (CameraRenderer.isRendering()) return;
        frameSnapshot.submit(context.submitNodeCollector());
    }

    public static RenderType getLayer(int textureId) {
        return getLayer(textureIdentifier(textureId));
    }

    public static RenderType getLayer(Identifier texture) {
        return RenderTypes.entityTranslucent(texture);
    }

    public static RenderType getTranslucentLayer(int textureId) {
        return getLayer(textureId);
    }

    public static RenderType getTranslucentLayer(Identifier texture) {
        return getLayer(texture);
    }

    public static RenderType getPremultipliedTranslucentLayer(Identifier texture) {
        return getLayer(texture);
    }

    public static RenderType getBackingLayer(int textureId) {
        return getLayer(textureId);
    }

    public static Identifier textureIdentifier(int textureId) {
        if (textureId < 0) return PLACEHOLDER_TEXTURE;
        ExternalTextureRegistry.Acquisition acquisition = EXTERNAL_TEXTURES.acquire(textureId);
        Identifier identifier = textureIdentifier(acquisition.registration());
        if (acquisition.created()) {
            Minecraft.getInstance().getTextureManager().register(identifier, new ExternalGlTexture(textureId, 1, 1));
        }
        return identifier;
    }

    public static void releaseTexture(int textureId) {
        if (textureId < 0) return;
        frameSnapshot = FrameRenderSnapshot.EMPTY;
        EXTERNAL_TEXTURES.release(textureId).ifPresent(ScreenRenderer::releaseTexture);
    }

    public static void clearExternalTextures() {
        frameSnapshot = FrameRenderSnapshot.EMPTY;
        List<ExternalTextureRegistry.Registration> registrations = EXTERNAL_TEXTURES.clear();
        runOnClientThread(() -> {
            for (ExternalTextureRegistry.Registration registration : registrations) {
                Minecraft.getInstance().getTextureManager().release(textureIdentifier(registration));
            }
        });
    }

    private static void releaseTexture(ExternalTextureRegistry.Registration registration) {
        Identifier identifier = textureIdentifier(registration);
        runOnClientThread(() -> Minecraft.getInstance().getTextureManager().release(identifier));
    }

    private static Identifier textureIdentifier(ExternalTextureRegistry.Registration registration) {
        return Identifier.fromNamespaceAndPath("videoplayer", registration.identifierPath());
    }

    private static void runOnClientThread(Runnable task) {
        Minecraft client = Minecraft.getInstance();
        if (client.isSameThread()) {
            task.run();
        } else {
            client.execute(task);
        }
    }

    public static int placeholderTextureId() {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(PLACEHOLDER_TEXTURE);
        if (texture.getTexture() instanceof com.mojang.blaze3d.opengl.GlTexture glTexture) {
            return glTexture.glId();
        }
        return -1;
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

    public static void drawGuiTexturedTriangles(GuiGraphicsExtractor context, int textureId, List<GuiVertex> vertices) {
        if (vertices == null || vertices.size() < 3) return;
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(textureIdentifier(textureId));
        Matrix3x2f pose = new Matrix3x2f(context.pose());
        int vertexCount = vertices.size() - vertices.size() % 3;
        List<GuiVertex> copiedVertices = List.copyOf(vertices.subList(0, vertexCount));
        ((DrawContextAccessor) context).videoplayer$getState().addGuiElement(new GuiTexturedTrianglesRenderState(
                TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler()),
                pose,
                copiedVertices,
                bounds(copiedVertices, pose)
        ));
    }

    private static ScreenRectangle bounds(List<GuiVertex> vertices, Matrix3x2f pose) {
        Vector2f transformed = new Vector2f();
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (GuiVertex vertex : vertices) {
            pose.transformPosition(vertex.x, vertex.y, transformed);
            minX = Math.min(minX, transformed.x);
            minY = Math.min(minY, transformed.y);
            maxX = Math.max(maxX, transformed.x);
            maxY = Math.max(maxY, transformed.y);
        }
        int x = (int) Math.floor(minX);
        int y = (int) Math.floor(minY);
        return new ScreenRectangle(x, y, Math.max(1, (int) Math.ceil(maxX) - x), Math.max(1, (int) Math.ceil(maxY) - y));
    }

    public record GuiVertex(float x, float y, float u, float v, int color) {
    }

    private record GuiTexturedTrianglesRenderState(TextureSetup textureSetup, Matrix3x2f pose,
                                                   List<GuiVertex> vertices, ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (int i = 0; i + 2 < vertices.size(); i += 3) {
                GuiVertex first = vertices.get(i);
                GuiVertex second = vertices.get(i + 1);
                GuiVertex third = vertices.get(i + 2);
                setupVertex(consumer, pose, first);
                setupVertex(consumer, pose, second);
                setupVertex(consumer, pose, third);
                setupVertex(consumer, pose, third);
            }
        }

        @Override
        public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
            return RenderPipelines.GUI_TEXTURED;
        }

        @Override
        public ScreenRectangle scissorArea() {
            return null;
        }
    }

    private static void setupVertex(VertexConsumer consumer, Matrix3x2f pose, GuiVertex vertex) {
        consumer.addVertexWith2DPose(pose, vertex.x, vertex.y).setUv(vertex.u, vertex.v).setColor(vertex.color);
    }
}
