package com.github.squi2rel.vp.network;

import com.github.squi2rel.vp.VideoPlayerMain;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record VideoPayload(byte[] data) implements FabricPacket {
    public static final ResourceLocation VIDEO_PAYLOAD_ID = new ResourceLocation(VideoPlayerMain.MOD_ID, "video");
    public static final PacketType<VideoPayload> ID = PacketType.create(VIDEO_PAYLOAD_ID, VideoPayload::new);

    public VideoPayload(FriendlyByteBuf buf) {
        this(readBytes(buf));
    }

    private static byte[] readBytes(FriendlyByteBuf buf) {
        if (buf.readableBytes() > VideoPackets.MAX_PAYLOAD_BYTES) {
            throw new IllegalStateException("VideoPlayer payload exceeds " + VideoPackets.MAX_PAYLOAD_BYTES + " bytes");
        }
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return data;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBytes(data);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }

    public static void register() {
    }
}
