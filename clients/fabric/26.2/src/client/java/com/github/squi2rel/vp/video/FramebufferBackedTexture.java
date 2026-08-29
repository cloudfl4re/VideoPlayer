package com.github.squi2rel.vp.video;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.texture.AbstractTexture;

public final class FramebufferBackedTexture extends AbstractTexture {
    private final RenderTarget framebuffer;

    public FramebufferBackedTexture(RenderTarget framebuffer) {
        this(framebuffer, FilterMode.LINEAR);
    }

    public FramebufferBackedTexture(RenderTarget framebuffer, FilterMode filterMode) {
        this.framebuffer = framebuffer;
        this.sampler = RenderSystem.getSamplerCache().getSampler(
                AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE,
                filterMode,
                filterMode,
                false
        );
        updateAttachment();
    }

    public void updateAttachment() {
        this.texture = framebuffer.getColorTexture();
        this.textureView = framebuffer.getColorTextureView();
    }

    @Override
    public void close() {
        framebuffer.destroyBuffers();
        texture = null;
        textureView = null;
    }
}
