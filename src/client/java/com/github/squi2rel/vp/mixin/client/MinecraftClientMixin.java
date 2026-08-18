package com.github.squi2rel.vp.mixin.client;

import com.github.squi2rel.vp.VideoPlayerClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    public void render(boolean tick, CallbackInfo ci) {
        VideoPlayerClient.updated = false;
    }
}
