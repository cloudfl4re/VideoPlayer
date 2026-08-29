package com.github.squi2rel.vp.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static com.github.squi2rel.vp.network.ByteBufUtils.writeString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServerPacketHandlerPacketDecodeTest {
    @Test
    void consumesSyncGenerationBeforeBusinessValidation() {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeString(buf, "area");
            writeString(buf, "screen");
            buf.writeLong(42L);

            ServerPacketHandler.ScreenGenerationRequest request = ServerPacketHandler.readScreenGenerationRequest(buf);

            assertEquals("area", request.areaName());
            assertEquals("screen", request.screenName());
            assertEquals(42L, request.generation());
            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }

    @Test
    void consumesSeekAndAutoSyncLongsBeforeBusinessValidation() {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeString(buf, "area");
            writeString(buf, "screen");
            buf.writeLong(42L);
            buf.writeLong(1234L);

            ServerPacketHandler.ScreenGenerationValueRequest request = ServerPacketHandler.readScreenGenerationValueRequest(buf);

            assertEquals("area", request.areaName());
            assertEquals("screen", request.screenName());
            assertEquals(42L, request.generation());
            assertEquals(1234L, request.value());
            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }

    @Test
    void preservesUnexpectedTrailingDataForStrictPacketCheck() {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeString(buf, "area");
            writeString(buf, "screen");
            buf.writeLong(42L);
            buf.writeLong(1234L);
            buf.writeByte(1);

            ServerPacketHandler.readScreenGenerationValueRequest(buf);

            assertEquals(1, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Test
    void consumesClientPlaybackResolutionBeforeBusinessValidation() {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeString(buf, "area");
            writeString(buf, "screen");
            buf.writeLong(42L);
            buf.writeLong(99L);
            buf.writeByte(ClientPlaybackResolution.FINITE.id);
            buf.writeLong(1234L);

            ServerPacketHandler.ClientPlaybackResolutionRequest request =
                    ServerPacketHandler.readClientPlaybackResolutionRequest(buf);

            assertEquals("area", request.areaName());
            assertEquals("screen", request.screenName());
            assertEquals(42L, request.generation());
            assertEquals(99L, request.reporterToken());
            assertEquals(ClientPlaybackResolution.FINITE, request.resolution());
            assertEquals(1234L, request.durationMs());
            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }

    @Test
    void preservesTrailingDataAfterClientPlaybackResolutionDecode() {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeString(buf, "area");
            writeString(buf, "screen");
            buf.writeLong(42L);
            buf.writeLong(99L);
            buf.writeByte(ClientPlaybackResolution.LIVE.id);
            buf.writeLong(0L);
            buf.writeByte(1);

            ServerPacketHandler.readClientPlaybackResolutionRequest(buf);

            assertEquals(1, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Test
    void decodesUnknownClientPlaybackResolutionWithoutLeavingPayloadBytes() {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeString(buf, "area");
            writeString(buf, "screen");
            buf.writeLong(42L);
            buf.writeLong(99L);
            buf.writeByte(127);
            buf.writeLong(1234L);

            ServerPacketHandler.ClientPlaybackResolutionRequest request =
                    ServerPacketHandler.readClientPlaybackResolutionRequest(buf);

            assertNull(request.resolution());
            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }
}
