package com.github.squi2rel.vp.video;

import com.github.squi2rel.vp.DataHolder;
import com.github.squi2rel.vp.i18n.VpTranslation;
import com.github.squi2rel.vp.network.ServerPacketHandler;
import com.github.squi2rel.vp.network.VideoPackets;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

import static com.github.squi2rel.vp.DataHolder.server;

public class ScreenBroadcaster {
    private final VideoScreen screen;

    public ScreenBroadcaster(VideoScreen screen) {
        this.screen = screen;
    }

    public void send(byte[] data) {
        if (server == null) return;
        PlayerManager pm = server.getPlayerManager();
        for (var uuid : screen.area.playerSnapshot()) {
            ServerPlayerEntity player = pm.getPlayer(uuid);
            if (player != null) {
                ServerPacketHandler.sendTo(player, data);
            }
        }
    }

    public void sendTo(java.util.UUID uuid, byte[] data) {
        if (uuid == null || data == null) return;
        if (server == null) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            ServerPacketHandler.sendTo(player, data);
        }
    }

    public void syncPlaylist() {
        send(VideoPackets.updatePlaylist(List.of(screen)));
    }

    public void syncIdlePlay() {
        if (server == null) return;
        PlayerManager pm = server.getPlayerManager();
        byte[] current = null;
        byte[] legacy = null;
        for (var uuid : screen.area.playerSnapshot()) {
            ServerPlayerEntity player = pm.getPlayer(uuid);
            if (player == null) continue;
            boolean mutations = DataHolder.supportsIdlePlayMutations(uuid);
            byte[] data;
            if (mutations) {
                if (current == null) current = VideoPackets.idlePlay(screen, true);
                data = current;
            } else {
                if (legacy == null) legacy = VideoPackets.idlePlay(screen, false);
                data = legacy;
            }
            ServerPacketHandler.sendTo(player, data);
        }
    }

    public void playbackNotice(VpTranslation message, boolean error) {
        for (var uuid : screen.area.playerSnapshot()) {
            DataHolder.message(uuid, screen.serverPluginEpoch(), message);
        }
    }
}
