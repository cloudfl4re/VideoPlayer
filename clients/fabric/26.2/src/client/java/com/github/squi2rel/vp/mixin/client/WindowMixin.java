package com.github.squi2rel.vp.mixin.client;

import com.github.squi2rel.vp.CameraRenderer;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public class WindowMixin {
    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    private void videoplayer$framebufferWidth(CallbackInfoReturnable<Integer> cir) {
        if (CameraRenderer.isRendering()) cir.setReturnValue(CameraRenderer.width);
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void videoplayer$framebufferHeight(CallbackInfoReturnable<Integer> cir) {
        if (CameraRenderer.isRendering()) cir.setReturnValue(CameraRenderer.height);
    }
}
