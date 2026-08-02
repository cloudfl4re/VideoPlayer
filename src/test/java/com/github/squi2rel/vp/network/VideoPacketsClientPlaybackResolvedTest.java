package com.github.squi2rel.vp.network;

import com.github.squi2rel.vp.video.VideoArea;
import com.github.squi2rel.vp.video.VideoScreen;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VideoPacketsClientPlaybackResolvedTest {
    @Test
    void writesTheClientResolutionPayloadInOrder() {
        VideoArea area = new VideoArea(new Vector3f(), new Vector3f(1), "area", "world");
        VideoScreen screen = new VideoScreen(area, "screen", new Vector3f(), new Vector3f(1, 0, 0),
                new Vector3f(1, 1, 0), new Vector3f(0, 1, 0), "");
        ByteBuf buf = Unpooled.wrappedBuffer(VideoPackets.clientPlaybackResolved(
                screen, 17L, 42L, ClientPlaybackResolution.FINITE, 12_345L
        ));
        try {
            assertEquals(VideoPacketType.CLIENT_PLAYBACK_RESOLVED, VideoPackets.readType(buf));
            assertEquals("area", VideoPackets.readName(buf));
            assertEquals("screen", VideoPackets.readName(buf));
            assertEquals(17L, buf.readLong());
            assertEquals(42L, buf.readLong());
            assertEquals(ClientPlaybackResolution.FINITE, ClientPlaybackResolution.fromId(buf.readUnsignedByte()));
            assertEquals(12_345L, buf.readLong());
        } finally {
            buf.release();
        }
    }

    @Test
    void writesTheReporterCapabilityPayloadInOrder() {
        VideoArea area = new VideoArea(new Vector3f(), new Vector3f(1), "area", "world");
        VideoScreen screen = new VideoScreen(area, "screen", new Vector3f(), new Vector3f(1, 0, 0),
                new Vector3f(1, 1, 0), new Vector3f(0, 1, 0), "");
        ByteBuf buf = Unpooled.wrappedBuffer(VideoPackets.clientPlaybackReporter(screen, 17L, 42L));
        try {
            assertEquals(VideoPacketType.CLIENT_PLAYBACK_REPORTER, VideoPackets.readType(buf));
            assertEquals("area", VideoPackets.readName(buf));
            assertEquals("screen", VideoPackets.readName(buf));
            assertEquals(17L, buf.readLong());
            assertEquals(42L, buf.readLong());
        } finally {
            buf.release();
        }
    }
}
