package com.github.squi2rel.vp.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

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
