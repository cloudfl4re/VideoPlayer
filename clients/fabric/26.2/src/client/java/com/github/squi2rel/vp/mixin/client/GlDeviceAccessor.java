package com.github.squi2rel.vp.mixin.client;

import com.mojang.blaze3d.opengl.FrameBufferCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public interface GlDeviceAccessor {
    @Invoker("frameBufferCache")
    FrameBufferCache videoplayer$getFrameBufferCache();
}
