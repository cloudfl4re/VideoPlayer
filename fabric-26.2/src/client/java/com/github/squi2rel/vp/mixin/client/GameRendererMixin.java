package com.github.squi2rel.vp.mixin.client;

import com.github.squi2rel.vp.CameraRenderer;
import com.github.squi2rel.vp.VideoPlayerClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void videoplayer$postUpdate(DeltaTracker tickCounter, CallbackInfo ci) {
        if (!CameraRenderer.isRendering()) VideoPlayerClient.postUpdate();
    }
}
