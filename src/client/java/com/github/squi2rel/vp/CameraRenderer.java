package com.github.squi2rel.vp;

import com.github.squi2rel.vp.mixin.client.MinecraftClientAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class CameraRenderer {
    public static boolean rendering;
    public static int width;
    public static int height;
    public static int fov = 70;

    private CameraRenderer() {
    }

    public static void renderWorld(Entity entity, RenderTarget framebuffer, int cameraFov) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || entity == null || framebuffer == null || rendering) return;
        MinecraftClientAccessor access = (MinecraftClientAccessor) client;
        RenderTarget oldFramebuffer = access.videoplayer$getFramebuffer();
        Entity oldCamera = client.getCameraEntity();
        width = framebuffer.width;
        height = framebuffer.height;
        fov = Math.clamp(cameraFov, 1, 179);
        rendering = true;
        try {
            access.videoplayer$setFramebuffer(framebuffer);
            client.setCameraEntity(entity);
            client.gameRenderer.renderLevel(client.getDeltaTracker());
        } finally {
            client.setCameraEntity(oldCamera);
            access.videoplayer$setFramebuffer(oldFramebuffer);
            rendering = false;
        }
    }
}
