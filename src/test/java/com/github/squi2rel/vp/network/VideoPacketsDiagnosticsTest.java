package com.github.squi2rel.vp.network;

import com.github.squi2rel.vp.video.PlaybackDiagnostics;
import com.github.squi2rel.vp.video.PlaybackFailureReason;
import com.github.squi2rel.vp.video.VideoArea;
import com.github.squi2rel.vp.video.VideoScreen;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VideoPacketsDiagnosticsTest {
    @Test
    void diagnosticsPacketRoundTripsWithoutPlaybackSourceUrls() {
        VideoArea area = new VideoArea(new Vector3f(), new Vector3f(2), "area", "world");
        VideoScreen screen = new VideoScreen(area, "screen", new Vector3f(), new Vector3f(1, 0, 0),
                new Vector3f(1, 1, 0), new Vector3f(0, 1, 0), "");
        PlaybackDiagnostics expected = new PlaybackDiagnostics(
                "Current title", "Queued title", 2, 41L, 12_000L,
                true, false, false, true, 2, 50_000L,
                PlaybackFailureReason.PLAYBACK_TIMEOUT, "The playback backend timed out while loading media",
                49_000L, true, true, "READY"
        );

        ByteBuf buf = Unpooled.wrappedBuffer(VideoPackets.diagnostics(screen, expected));
        try {
            assertEquals(VideoPacketType.DIAGNOSTICS, VideoPackets.readType(buf));
            assertEquals("area", VideoPackets.readName(buf));
            assertEquals("screen", VideoPackets.readName(buf));
            assertEquals(expected, VideoPackets.readDiagnostics(buf));
        } finally {
            buf.release();
        }
    }
}
