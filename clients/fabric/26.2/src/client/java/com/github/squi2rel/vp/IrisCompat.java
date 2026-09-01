package com.github.squi2rel.vp;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;

final class IrisCompat {
    private IrisCompat() {
    }

    static void registerTexturedPipelines(RenderPipeline... pipelines) {
        IrisApi api = IrisApi.getInstance();
        for (RenderPipeline pipeline : pipelines) {
            api.assignPipeline(pipeline, IrisProgram.TEXTURED);
        }
    }
}
