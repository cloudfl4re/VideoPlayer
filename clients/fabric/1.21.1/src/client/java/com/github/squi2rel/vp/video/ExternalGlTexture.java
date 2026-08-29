package com.github.squi2rel.vp.video;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

public final class ExternalGlTexture extends AbstractTexture {
    public ExternalGlTexture(int glId) {
        this.id = glId;
        setFilter(true, false);
    }

    @Override
    public void load(ResourceManager resourceManager) {
    }

    @Override
    public void close() {
        id = NOT_ASSIGNED;
    }
}
