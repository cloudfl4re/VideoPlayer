package com.github.squi2rel.vp.network;

import com.github.squi2rel.vp.video.VideoArea;
import com.github.squi2rel.vp.video.VideoScreen;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdlePlayMutationPacketTest {
    @Test
    void roundTripsEveryMutationWithoutAnOwnerField() {
        UUID id = UUID.randomUUID();
        assertRoundTrip(IdlePlayMutation.add("https://example.com/video", 75));
        assertRoundTrip(IdlePlayMutation.remove(id));
        assertRoundTrip(IdlePlayMutation.setPriority(id, 20));
        assertRoundTrip(IdlePlayMutation.clear());
        assertRoundTrip(IdlePlayMutation.setMode(true));
    }

    @Test
    void rejectsUnknownActionsAndOutOfRangePriorities() {
        ByteBuf unknown = Unpooled.buffer();
        try {
            unknown.writeByte(99);
            assertThrows(IllegalStateException.class, () -> VideoPackets.readIdlePlayMutation(unknown));
        } finally {
            unknown.release();
        }

        ByteBuf priority = Unpooled.buffer();
        try {
            priority.writeByte(IdlePlayAction.ADD.id());
            ByteBufUtils.writeString(priority, "https://example.com/video");
            priority.writeByte(101);
            assertThrows(IllegalStateException.class, () -> VideoPackets.readIdlePlayMutation(priority));
        } finally {
            priority.release();
        }
    }

    @Test
    void roundTripsTheVp2IdlePlaySnapshot() {
        List<String> urls = List.of("https://example.com/first", "https://example.com/second");
        ByteBuf config = Unpooled.buffer();
        try {
            VideoPackets.writeLegacyIdlePlayConfig(config, urls, true);
            VideoPackets.LegacyIdlePlayConfig decoded = VideoPackets.readLegacyIdlePlayConfig(config);
            assertEquals(urls, decoded.urls());
            assertEquals(true, decoded.random());
            assertFalse(config.isReadable());
        } finally {
            config.release();
        }

        VideoArea area = new VideoArea(new Vector3f(), new Vector3f(1), "area", "world");
        VideoScreen screen = new VideoScreen(area, "screen", List.of(), "");
        screen.setIdlePlayConfig(urls, false);
        ByteBuf packet = Unpooled.wrappedBuffer(VideoPackets.idlePlay(screen, false));
        try {
            assertEquals(VideoPacketType.IDLE_PLAY, VideoPackets.readType(packet));
            assertEquals("area", VideoPackets.readName(packet));
            assertEquals("screen", VideoPackets.readName(packet));
            VideoPackets.LegacyIdlePlayConfig decoded = VideoPackets.readLegacyIdlePlayConfig(packet);
            assertEquals(urls, decoded.urls());
            assertEquals(false, decoded.random());
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    private static void assertRoundTrip(IdlePlayMutation expected) {
        ByteBuf buf = Unpooled.buffer();
        try {
            VideoPackets.writeIdlePlayMutation(buf, expected);
            IdlePlayMutation actual = VideoPackets.readIdlePlayMutation(buf);
            assertEquals(expected, actual);
            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }
}
