package com.github.squi2rel.vp;

import com.github.squi2rel.vp.network.VideoHandshakeState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoHandshakeRejectionTest {
    @Test
    void reportsOnlyTheFirstRejectionForAPlayer() {
        UUID uuid = UUID.randomUUID();
        try {
            assertTrue(DataHolder.rejectHandshake(uuid));
            assertEquals(VideoHandshakeState.REJECTED, DataHolder.handshakeState(uuid));
            assertFalse(DataHolder.rejectHandshake(uuid));
        } finally {
            DataHolder.playerLeave(uuid);
        }
    }

    @Test
    void reportsOnlyActualHandshakeTokenChanges() {
        UUID uuid = UUID.randomUUID();
        String previousVersion = VideoPlayerMain.version;
        try {
            VideoPlayerMain.version = "2.0.2";
            assertTrue(DataHolder.recordHandshakeToken(uuid, "2.0.1|vp2"));
            assertFalse(DataHolder.recordHandshakeToken(uuid, "2.0.1|vp2"));
            assertTrue(DataHolder.recordHandshakeToken(uuid, "2.0.2|vp5"));
            assertFalse(DataHolder.recordHandshakeToken(uuid, "2.0.2|vp5"));
        } finally {
            VideoPlayerMain.version = previousVersion;
            DataHolder.playerLeave(uuid);
        }
    }
}
