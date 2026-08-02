package com.github.squi2rel.vp.video;

import com.github.squi2rel.vp.i18n.VpTexts;
import com.github.squi2rel.vp.provider.VideoInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Formatting;

import static com.github.squi2rel.vp.VideoPlayerMain.LOGGER;

final class UnavailableVideoBackend implements VideoBackend {
    private final String requestedBackend;
    private boolean warned;

    UnavailableVideoBackend(String requestedBackend) {
        this.requestedBackend = VideoBackends.normalize(requestedBackend);
    }

    @Override
    public String name() {
        return requestedBackend;
    }

    @Override
    public void init() {
    }

    @Override
    public void play(VideoInfo info, long targetTime, int volume) {
        if (warned) return;
        warned = true;
        LOGGER.warn("No available video backend for requested backend {}", requestedBackend);
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player != null) {
            player.sendMessage(VpTexts.tr(
                    "error.videoplayer.local_backend_unavailable",
                    "Neither local MPV nor VLC is available. Open /videoplayer boot to install or repair a video runtime."
            ).formatted(Formatting.RED), false);
        }
    }

    @Override
    public void updateTexture() {
    }

    @Override
    public int getTextureId() {
        return -1;
    }

    @Override
    public int getWidth() {
        return 1;
    }

    @Override
    public int getHeight() {
        return 1;
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean canPause() {
        return false;
    }

    @Override
    public void pause(boolean pause) {
    }

    @Override
    public boolean isPaused() {
        return true;
    }

    @Override
    public void setVolume(int volume) {
    }

    @Override
    public boolean canSetProgress() {
        return false;
    }

    @Override
    public void setProgress(long progress) {
    }

    @Override
    public long getProgress() {
        return 0;
    }

    @Override
    public long getTotalProgress() {
        return 0;
    }

    @Override
    public void setRate(float rate) {
    }

    @Override
    public float getRate() {
        return 1;
    }

    @Override
    public void cleanup() {
    }
}
