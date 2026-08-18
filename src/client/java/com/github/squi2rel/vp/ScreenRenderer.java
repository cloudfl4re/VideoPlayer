package com.github.squi2rel.vp;

import com.github.squi2rel.vp.video.ClientVideoScreen;
import com.github.squi2rel.vp.danmaku.ClientDanmakuRenderer;
import com.github.squi2rel.vp.video.ExternalGlTexture;
import com.github.squi2rel.vp.mixin.client.DrawContextAccessor;
import com.github.squi2rel.vp.render.ExternalTextureRegistry;
import com.github.squi2rel.vp.vivecraft.Vivecraft;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

import static com.github.squi2rel.vp.VideoPlayerClient.*;

@SuppressWarnings({"resource", "DataFlowIssue"})
public class ScreenRenderer {
    private static final String SAMPLER = "Sampler0";
    private static final Identifier PLACEHOLDER_TEXTURE = Identifier.fromNamespaceAndPath("videoplayer", "placeholder.png");
    private static final ExternalTextureRegistry EXTERNAL_TEXTURES = new ExternalTextureRegistry();
    private static final Map<LayerKey, RenderType> layers = new HashMap<>();
    private static final RenderPipeline VIDEO_WORLD_QUADS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("videoplayer", "video_world_quads"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .build());
    private static final RenderPipeline VIDEO_WORLD_TRIANGLE_STRIP = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("videoplayer", "video_world_triangle_strip"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .build());
    private static final RenderPipeline VIDEO_WORLD_PREMULTIPLIED_QUADS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("videoplayer", "video_world_premultiplied_quads"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withDepthWrite(false)
            .build());
    private static final RenderPipeline VIDEO_GUI_QUADS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("videoplayer", "video_gui_quads"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .build());
    private static final RenderPipeline VIDEO_GUI_TRIANGLES = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("videoplayer", "video_gui_triangles"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .build());
    private static final RenderPipeline GUI_COLOR_QUADS_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("videoplayer", "gui_color_quads"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .build());

    static {
        registerIrisPipeline(VIDEO_WORLD_QUADS, "TEXTURED_COLOR");
        registerIrisPipeline(VIDEO_WORLD_TRIANGLE_STRIP, "TEXTURED_COLOR");
    }

    private static final Quaternionf rotation = new Quaternionf();
    public static float cameraX, cameraY, cameraZ;
    public static double preciseCameraX, preciseCameraY, preciseCameraZ;
    public static boolean skybox;

    public static void render(WorldRenderContext ctx) {
        if (CameraRenderer.rendering) return;
        skybox = false;
        ProfilerFiller profiler = Profiler.get();
        profiler.push("video");
        profiler.push("render");
        PoseStack matrices = ctx.matrices();
        matrices.pushPose();
        Camera cameraObject = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camera = cameraObject.position();
        preciseCameraX = camera.x;
        preciseCameraY = camera.y;
        preciseCameraZ = camera.z;
        cameraX = (float) preciseCameraX;
        cameraY = (float) preciseCameraY;
        cameraZ = (float) preciseCameraZ;
        if (Vivecraft.loaded && Vivecraft.isVRActive()) {
            rotation.setFromNormalized(Vivecraft.getRotation()).invert();
        } else {
            cameraObject.rotation().invert(rotation);
        }
        ClientDanmakuRenderer.beginFrame(screens);
        ByteBufferBuilder allocator = new ByteBufferBuilder(4096);
        try {
            MultiBufferSource.BufferSource consumers = MultiBufferSource.immediate(allocator);
            for (ClientVideoScreen screen : screens) {
                try {
                    screen.draw(matrices, consumers);
                } catch (Exception e) {
                    VideoPlayerMain.LOGGER.error("Exception while rendering", e);
                }
            }
            consumers.endBatch();
        } catch (RuntimeException e) {
            VideoPlayerMain.LOGGER.warn("Failed to draw video screen buffers", e);
        } finally {
            allocator.close();
        }
        matrices.popPose();
        profiler.pop();
        profiler.pop();
    }

    public static RenderType getLayer(int textureId) {
        return texturedLayer(textureId, LayerKind.WORLD);
    }

    public static RenderType getLayer(Identifier texture) {
        return texturedLayer(texture, LayerKind.WORLD);
    }

    public static RenderType getTranslucentLayer(int textureId) {
        return texturedLayer(textureId, LayerKind.WORLD_TRANSLUCENT);
    }

    public static RenderType getTranslucentLayer(Identifier texture) {
        return texturedLayer(texture, LayerKind.WORLD_TRANSLUCENT);
    }

    public static RenderType getPremultipliedTranslucentLayer(Identifier texture) {
        return texturedLayer(texture, LayerKind.WORLD_PREMULTIPLIED_TRANSLUCENT);
    }

    public static void removeTextureLayers(Identifier texture) {
        layers.keySet().removeIf(key -> key.textureId instanceof Identifier identifier && identifier.equals(texture));
    }

    public static RenderType getBackingLayer(int textureId) {
        return texturedLayer(textureId, LayerKind.WORLD_BACKING);
    }

    public static RenderType getGuiLayer(int textureId) {
        return texturedLayer(textureId, LayerKind.GUI_QUADS);
    }

    public static RenderType getGuiTriangleLayer(int textureId) {
        return texturedLayer(textureId, LayerKind.GUI_TRIANGLES);
    }

    public static RenderType getGuiColorQuadLayer() {
        return layers.computeIfAbsent(new LayerKey(0, LayerKind.GUI_COLOR_QUADS), key ->
                RenderType.create("videoplayer_gui_color_quads", RenderSetup.builder(GUI_COLOR_QUADS_PIPELINE)
                        .bufferSize(256)
                        .sortOnUpload()
                        .createRenderSetup()));
    }

    public static void drawGuiLayer(RenderType layer, Consumer<VertexConsumer> drawer) {
        BufferBuilder buffer = Tesselator.getInstance().begin(layer.mode(), layer.format());
        drawer.accept(buffer);
        MeshData built = buffer.build();
        if (built != null) {
            layer.draw(built);
        }
    }

    public static void drawWorldTexturedMesh(int textureId, Matrix4f modelMatrix, GpuBuffer vertexBuffer,
                                             int vertexCount, int textureColor) {
        if (textureId < 0 || modelMatrix == null || vertexBuffer == null || vertexBuffer.isClosed() || vertexCount <= 0) {
            return;
        }

        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(textureIdentifier(textureId));
        if (texture.getTextureView() == null || texture.getSampler() == null) return;

        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(modelMatrix);
        Matrix4f textureTransform = new Matrix4f();
        Vector3f modelOffset = new Vector3f();
        GpuBufferSlice textureTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                modelView,
                color(textureColor),
                modelOffset,
                textureTransform
        );

        var framebuffer = Minecraft.getInstance().getMainRenderTarget();
        GpuTextureView colorTarget = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputColorTextureOverride
                : framebuffer.getColorTextureView();
        GpuTextureView depthTarget = framebuffer.useDepth
                ? (RenderSystem.outputDepthTextureOverride != null
                ? RenderSystem.outputDepthTextureOverride
                : framebuffer.getDepthTextureView())
                : null;

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "videoplayer_360_mesh",
                colorTarget,
                OptionalInt.empty(),
                depthTarget,
                OptionalDouble.empty()
        )) {
            pass.setPipeline(VIDEO_WORLD_TRIANGLE_STRIP);
            var scissor = RenderSystem.getScissorStateForRenderTypeDraws();
            if (scissor.enabled()) {
                pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
            }
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture(SAMPLER, texture.getTextureView(), texture.getSampler());
            pass.setVertexBuffer(0, vertexBuffer);
            pass.setUniform("DynamicTransforms", textureTransforms);
            pass.draw(0, vertexCount);
        }
    }

    public static void drawGuiTexturedTriangles(GuiGraphics context, int textureId, List<GuiVertex> vertices) {
        if (vertices == null || vertices.size() < 3) return;
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(textureIdentifier(textureId));
        Matrix3x2f pose = new Matrix3x2f(context.pose());
        int vertexCount = vertices.size() - vertices.size() % 3;
        List<GuiVertex> copiedVertices = List.copyOf(vertices.subList(0, vertexCount));
        ((DrawContextAccessor) context).videoplayer$getState().submitGuiElement(new GuiTexturedTrianglesRenderState(
                TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler()),
                pose,
                copiedVertices,
                bounds(copiedVertices, pose)
        ));
    }

    public static void drawGuiPremultipliedTexturedQuad(GuiGraphics context, Identifier identifier,
                                                        int x1, int y1, int x2, int y2,
                                                        float u1, float u2, float v1, float v2,
                                                        int color) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(identifier);
        ((DrawContextAccessor) context).videoplayer$getState().submitGuiElement(new BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler()),
                new Matrix3x2f(context.pose()),
                x1,
                y1,
                x2,
                y2,
                u1,
                u2,
                v1,
                v2,
                color,
                context.scissorStack.peek()
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
        int width = Math.max(1, (int) Math.ceil(maxX) - x);
        int height = Math.max(1, (int) Math.ceil(maxY) - y);
        return new ScreenRectangle(x, y, width, height);
    }

    private static RenderType texturedLayer(int textureId, LayerKind kind) {
        return texturedLayer(textureIdentifier(textureId), kind);
    }

    private static RenderType texturedLayer(Identifier texture, LayerKind kind) {
        return layers.computeIfAbsent(new LayerKey(texture, kind), key ->
                RenderType.create("videoplayer_" + kind.name().toLowerCase() + "_" + texture.toString().replace(':', '_').replace('/', '_'), setup(texture, kind)));
    }

    private static RenderSetup setup(Identifier texture, LayerKind kind) {
        RenderSetup.RenderSetupBuilder builder = RenderSetup.builder(kind.pipeline)
                .withTexture(SAMPLER, texture)
                .bufferSize(kind.expectedBufferSize);
        if (kind.useOverlay) builder.useOverlay();
        if (kind.useLightmap) builder.useLightmap();
        if (kind.translucent) builder.sortOnUpload();
        return builder.createRenderSetup();
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
        EXTERNAL_TEXTURES.release(textureId).ifPresent(ScreenRenderer::releaseTexture);
    }

    public static void clearExternalTextures() {
        List<ExternalTextureRegistry.Registration> registrations = EXTERNAL_TEXTURES.clear();
        runOnClientThread(() -> {
            for (ExternalTextureRegistry.Registration registration : registrations) {
                Minecraft.getInstance().getTextureManager().release(textureIdentifier(registration));
            }
            layers.clear();
        });
    }

    private static void releaseTexture(ExternalTextureRegistry.Registration registration) {
        Identifier identifier = textureIdentifier(registration);
        runOnClientThread(() -> {
            Minecraft.getInstance().getTextureManager().release(identifier);
            layers.keySet().removeIf(key -> key.textureId.equals(identifier));
        });
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
        if (Minecraft.getInstance().getTextureManager().getTexture(PLACEHOLDER_TEXTURE).getTexture() instanceof GlTexture texture) {
            return texture.glId();
        }
        return -1;
    }

    public static void rotateMatrix(PoseStack matrices) {
        matrices.mulPose(rotation);
    }

    public static void drawWorldTexturedVertex(Matrix4f matrix, VertexConsumer consumer, Vector3f vertex,
                                               float u, float v, int color, Vector3f normal) {
        drawWorldTexturedVertex(matrix, consumer, vertex.x, vertex.y, vertex.z, u, v, color, 0.0f, 0.0f, 0.0f);
    }

    public static void drawWorldTexturedVertex(Matrix4f matrix, VertexConsumer consumer,
                                               float x, float y, float z, float u, float v, int color,
                                               float nx, float ny, float nz) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(color)
                .setUv(u, v);
    }

    private static Vector4f color(int color) {
        float a = ((color >>> 24) & 0xFF) / 255.0f;
        float r = ((color >>> 16) & 0xFF) / 255.0f;
        float g = ((color >>> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        return new Vector4f(r, g, b, a);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerIrisPipeline(RenderPipeline pipeline, String shaderKeyName) {
        try {
            Class<?> shaderKeyClass = Class.forName("net.irisshaders.iris.pipeline.programs.ShaderKey");
            Object shaderKey = Enum.valueOf((Class<? extends Enum>) shaderKeyClass.asSubclass(Enum.class), shaderKeyName);
            Class<?> pipelinesClass = Class.forName("net.irisshaders.iris.pipeline.IrisPipelines");
            pipelinesClass.getMethod("assignPipeline", RenderPipeline.class, shaderKeyClass).invoke(null, pipeline, shaderKey);
            VideoPlayerMain.LOGGER.info("Registered Iris shader mapping {} -> {}", pipeline.getLocation(), shaderKeyName);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            // Iris is optional.
        } catch (LinkageError e) {
            VideoPlayerMain.LOGGER.warn("Failed to load Iris shader mapping hooks for {}", pipeline.getLocation(), e);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalStateException && cause.getMessage() != null && cause.getMessage().contains("Shader already assigned")) {
                return;
            }
            VideoPlayerMain.LOGGER.warn("Failed to register Iris shader mapping for {}", pipeline.getLocation(), e);
        } catch (RuntimeException e) {
            VideoPlayerMain.LOGGER.warn("Failed to register Iris shader mapping for {}", pipeline.getLocation(), e);
        }
    }

    private record LayerKey(Object textureId, LayerKind kind) {
    }

    public record GuiVertex(float x, float y, float u, float v, int color) {
    }

    private record GuiTexturedTrianglesRenderState(TextureSetup textureSetup, Matrix3x2f pose, List<GuiVertex> vertices,
                                                   ScreenRectangle bounds) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer) {
            // The vanilla GUI renderer indexes simple elements with a quad index buffer.
            // Submit each triangle as a degenerate quad so every triangle survives batching.
            for (int i = 0; i + 2 < vertices.size(); i += 3) {
                GuiVertex a = vertices.get(i);
                GuiVertex b = vertices.get(i + 1);
                GuiVertex c = vertices.get(i + 2);
                setupVertex(consumer, pose, a);
                setupVertex(consumer, pose, b);
                setupVertex(consumer, pose, c);
                setupVertex(consumer, pose, c);
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return VIDEO_GUI_QUADS;
        }

        @Override
        public ScreenRectangle scissorArea() {
            return null;
        }
    }

    private static void setupVertex(VertexConsumer consumer, Matrix3x2f pose, GuiVertex vertex) {
        consumer.addVertexWith2DPose(pose, vertex.x, vertex.y).setUv(vertex.u, vertex.v).setColor(vertex.color);
    }

    private enum LayerKind {
        WORLD(VIDEO_WORLD_QUADS, 4096, false, false, false),
        WORLD_TRANSLUCENT(VIDEO_WORLD_QUADS, 4096, false, false, true),
        WORLD_PREMULTIPLIED_TRANSLUCENT(VIDEO_WORLD_PREMULTIPLIED_QUADS, 4096, false, false, true),
        WORLD_BACKING(VIDEO_WORLD_QUADS, 4096, false, false, false),
        GUI_QUADS(VIDEO_GUI_QUADS, 256, false, false, true),
        GUI_TRIANGLES(VIDEO_GUI_TRIANGLES, 256, false, false, true),
        GUI_COLOR_QUADS(GUI_COLOR_QUADS_PIPELINE, 256, false, false, true);

        private final RenderPipeline pipeline;
        private final int expectedBufferSize;
        private final boolean useOverlay;
        private final boolean useLightmap;
        private final boolean translucent;

        LayerKind(RenderPipeline pipeline, int expectedBufferSize, boolean useOverlay, boolean useLightmap, boolean translucent) {
            this.pipeline = pipeline;
            this.expectedBufferSize = expectedBufferSize;
            this.useOverlay = useOverlay;
            this.useLightmap = useLightmap;
            this.translucent = translucent;
        }
    }
}
