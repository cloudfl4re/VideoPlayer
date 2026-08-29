package com.github.squi2rel.vp;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

final class UnlitVideoRenderType {
    private static final Map<ResourceLocation, RenderType> LAYERS = new ConcurrentHashMap<>();

    private UnlitVideoRenderType() {
    }

    static RenderType get(ResourceLocation texture) {
        return LAYERS.computeIfAbsent(texture, UnlitVideoRenderType::create);
    }

    static void release(ResourceLocation texture) {
        LAYERS.remove(texture);
    }

    static void clear() {
        LAYERS.clear();
    }

    private static RenderType create(ResourceLocation texture) {
        String name = "videoplayer_unlit_" + texture.toString().replace(':', '_').replace('/', '_');
        return new RenderType(
                name,
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                true,
                true,
                () -> {
                    RenderSystem.setShader(GameRenderer::getRendertypeEyesShader);
                    RenderSystem.setShaderTexture(0, texture);
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthMask(true);
                    RenderSystem.colorMask(true, true, true, true);
                    RenderSystem.disableCull();
                },
                () -> {
                    RenderSystem.enableCull();
                    RenderSystem.disableBlend();
                    RenderSystem.defaultBlendFunc();
                }
        ) {
        };
    }
}
