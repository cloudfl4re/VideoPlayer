package com.github.squi2rel.vp.network;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ClientMessageBridge {
    private ClientMessageBridge() {
    }

    public static void sendOverlay(ServerPlayer player, Component message) {
        player.sendOverlayMessage(message);
    }
}
