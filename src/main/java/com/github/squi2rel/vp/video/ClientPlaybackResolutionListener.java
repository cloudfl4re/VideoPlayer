package com.github.squi2rel.vp.video;

interface ClientPlaybackResolutionListener {
    boolean resolveFinite(long durationMs);

    boolean resolveLive();
}
