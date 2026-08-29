package com.github.squi2rel.vp.network;

import com.github.squi2rel.vp.VideoPlayerMain;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record VideoPayload(byte[] data) implements CustomPacketPayload {
    public static final Identifier VIDEO_PAYLOAD_ID = Identifier.fromNamespaceAndPath(VideoPlayerMain.MOD_ID, "video");
    public static final CustomPacketPayload.Type<VideoPayload> ID = new CustomPacketPayload.Type<>(VIDEO_PAYLOAD_ID);
    public static final StreamCodec<FriendlyByteBuf, VideoPayload> CODEC = StreamCodec.ofMember((p, buf) -> buf.writeBytes(p.data), buf -> {
        if (buf.readableBytes() > VideoPackets.MAX_PAYLOAD_BYTES) {
            throw new IllegalStateException("VideoPlayer payload exceeds " + VideoPackets.MAX_PAYLOAD_BYTES + " bytes");
        }
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return new VideoPayload(data);
    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(ID, CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ID, CODEC);
    }
}
