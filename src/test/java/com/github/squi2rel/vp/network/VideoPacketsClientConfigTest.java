package com.github.squi2rel.vp.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VideoPacketsClientConfigTest {
    @Test
    void carriesTheCurrentTokenForA204Client() {
        ByteBuf buf = Unpooled.wrappedBuffer(VideoPackets.clientConfig("2.0.4"));
        try {
            assertEquals(VideoPacketType.CONFIG, VideoPackets.readType(buf));
            assertEquals("2.0.4|vp5", ByteBufUtils.readString(buf, VideoProtocol.MAX_TOKEN_BYTES));
            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }

    @Test
    void carriesTheReleased202TokenForA203Client() {
        ByteBuf buf = Unpooled.wrappedBuffer(VideoPackets.clientConfig("2.0.3"));
        try {
            assertEquals(VideoPacketType.CONFIG, VideoPackets.readType(buf));
            assertEquals("2.0.2|vp5", ByteBufUtils.readString(buf, VideoProtocol.MAX_TOKEN_BYTES));
            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }
}
