package com.github.squi2rel.vp.render;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CameraRenderGuard {
    private final AtomicBoolean rendering = new AtomicBoolean();

    public Scope enter() {
        return rendering.compareAndSet(false, true) ? new Scope(this) : null;
    }

    public boolean isRendering() {
        return rendering.get();
    }

    private void exit() {
        rendering.set(false);
    }

    public static final class Scope implements AutoCloseable {
        private CameraRenderGuard owner;

        private Scope(CameraRenderGuard owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            CameraRenderGuard current = owner;
            owner = null;
            if (current != null) current.exit();
        }
    }
}
