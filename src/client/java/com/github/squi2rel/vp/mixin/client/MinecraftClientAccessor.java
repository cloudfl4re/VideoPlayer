package com.github.squi2rel.vp.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {
    @Accessor("mainRenderTarget")
    RenderTarget videoplayer$getFramebuffer();

    @Accessor("mainRenderTarget")
    @Mutable
    void videoplayer$setFramebuffer(RenderTarget framebuffer);
}
