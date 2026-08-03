package com.github.squi2rel.vp.video;

import com.github.squi2rel.vp.DataHolder;
import com.github.squi2rel.vp.i18n.VpTranslation;
import com.github.squi2rel.vp.network.VideoPackets;

import java.util.List;
import java.util.UUID;

public class ScreenBroadcaster {
    private final VideoScreen screen;

    public ScreenBroadcaster(VideoScreen screen) {
        this.screen = screen;
    }

    public void send(byte[] data) {
        for (UUID uuid : screen.area.playerSnapshot()) {
            DataHolder.sendTo(uuid, data);
        }
    }

    public void sendTo(UUID uuid, byte[] data) {
        if (uuid == null || data == null) return;
        DataHolder.sendTo(uuid, data);
    }

    public void syncPlaylist() {
        send(VideoPackets.updatePlaylist(List.of(screen)));
    }

    public void syncIdlePlay() {
        byte[] current = null;
        byte[] legacy = null;
        for (UUID uuid : screen.area.playerSnapshot()) {
            boolean mutations = DataHolder.supportsIdlePlayMutations(uuid);
            byte[] data;
            if (mutations) {
                if (current == null) current = VideoPackets.idlePlay(screen, true);
                data = current;
            } else {
                if (legacy == null) legacy = VideoPackets.idlePlay(screen, false);
                data = legacy;
            }
            DataHolder.sendTo(uuid, data);
        }
    }

    public void playbackNotice(VpTranslation message, boolean error) {
        for (UUID uuid : screen.area.playerSnapshot()) {
            DataHolder.message(uuid, message);
        }
    }
}
