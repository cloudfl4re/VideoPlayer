package com.github.squi2rel.vp;

import com.github.squi2rel.vp.mixin.client.GameRendererTargetAccessor;
import com.github.squi2rel.vp.render.CameraRenderGuard;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class CameraRenderer {
    private static final CameraRenderGuard GUARD = new CameraRenderGuard();
    public static int width;
    public static int height;
    public static int fov = 70;

    private CameraRenderer() {
    }

    public static boolean isRendering() {
        return GUARD.isRendering();
    }

    public static void renderWorld(Entity entity, RenderTarget framebuffer, int cameraFov) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || entity == null || framebuffer == null) return;
        CameraRenderGuard.Scope scope = GUARD.enter();
        if (scope == null) return;
        GameRendererTargetAccessor access = (GameRendererTargetAccessor) client.gameRenderer;
        RenderTarget oldFramebuffer = access.videoplayer$getFramebuffer();
        Entity oldCamera = client.getCameraEntity();
        int oldWidth = width;
        int oldHeight = height;
        int oldFov = fov;
        width = framebuffer.width;
        height = framebuffer.height;
        fov = Math.clamp(cameraFov, 1, 179);
        try {
            access.videoplayer$setFramebuffer(framebuffer);
            client.setCameraEntity(entity);
            client.gameRenderer.extract(client.getDeltaTracker(), true);
            client.gameRenderer.renderLevel(client.getDeltaTracker());
        } finally {
            try {
                client.setCameraEntity(oldCamera);
            } finally {
                try {
                    width = oldWidth;
                    height = oldHeight;
                    fov = oldFov;
                    access.videoplayer$setFramebuffer(oldFramebuffer);
                } finally {
                    scope.close();
                }
            }
        }
    }
}
