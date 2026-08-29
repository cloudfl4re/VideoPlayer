package com.github.squi2rel.vp.video;

import com.github.squi2rel.vp.CameraRenderer;
import com.github.squi2rel.vp.provider.EntityViewProvider;
import com.github.squi2rel.vp.provider.VideoInfo;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public class EntityCameraPlayer extends AbstractCameraPlayer {
    private Entity entity;
    private UUID uuid;
    private int fov = 70;

    public EntityCameraPlayer(ClientVideoScreen screen) {
        super(screen);
    }

    @Override
    public void play(VideoInfo info) {
        uuid = EntityViewProvider.canonicalUuid(info.rawPath());
        entity = findEntity(uuid);
        rendered = false;
    }

    @Override
    public void stop() {
        entity = null;
        uuid = null;
        rendered = false;
    }

    @Override
    public void updateTexture() {
        if (uuid == null) return;
        if (entity == null || entity.isRemoved()) entity = findEntity(uuid);
        if (entity == null) {
            rendered = false;
            return;
        }
        super.updateTexture();
        CameraRenderer.renderWorld(entity, framebuffer, fov);
        rendered = true;
    }

    @Override
    public void onMetaChanged() {
        super.onMetaChanged();
        fov = screen.metadata.getInt(ScreenMetadata.KEY_CAMERA_FOV, 70);
    }

    private static Entity findEntity(UUID uuid) {
        Minecraft client = Minecraft.getInstance();
        if (uuid == null || client.level == null) return null;
        for (Entity candidate : client.level.entitiesForRendering()) {
            if (uuid.equals(candidate.getUUID())) return candidate;
        }
        return null;
    }
}
