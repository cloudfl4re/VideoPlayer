package com.github.squi2rel.vp.network;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoProtocolTest {
    @Test
    void createsAndMatchesTheCurrentWireToken() {
        assertEquals("2.0.1|vp5", VideoProtocol.token("2.0.1"));
        assertTrue(VideoProtocol.compatible("2.0.1", "2.0.1|vp5"));
        assertTrue(VideoProtocol.compatible("2.0.1", " 2.0.1|vp5"));
        assertTrue(VideoProtocol.compatible("2.0.1", "2.0.1|vp5 "));
    }

    @Test
    void acceptsEveryWireRevisionForTheSameRelease() {
        assertTrue(VideoProtocol.compatible("2.0.1", "2.0.1|vp5"));
        assertTrue(VideoProtocol.compatible("2.0.1", "2.0.1"));
        assertTrue(VideoProtocol.compatible("2.0.1", "2.0.1|vp2"));
        assertTrue(VideoProtocol.compatible("2.0.1", "2.0.1|custom-build"));
    }

    @Test
    void enforcesTheReleaseVersionMatrix() {
        List<CompatibilityCase> cases = List.of(
                new CompatibilityCase("2.0.1", "2.0.1|vp5", true),
                new CompatibilityCase("2.0.1", "2.0.2|vp5", false),
                new CompatibilityCase("2.0.1", "2.0.10|vp5", false),
                new CompatibilityCase("2.0.1", "2.0.1|vp1", true),
                new CompatibilityCase("2.0.1", "2.0.1|vp5-extra", true),
                new CompatibilityCase("2.0.1", "2.0.1|", true),
                new CompatibilityCase("2.0.1", "2.0.1", true),
                new CompatibilityCase("2.0.1", "", false),
                new CompatibilityCase("2.0.1", null, false)
        );

        for (CompatibilityCase testCase : cases) {
            assertEquals(
                    testCase.expected(),
                    VideoProtocol.compatible(testCase.localVersion(), testCase.remoteToken()),
                    () -> testCase.localVersion() + " against " + testCase.remoteToken()
            );
        }
    }

    @Test
    void respondsWithTheLocalTokenUnlessThePeerTokenIsCompatible() {
        assertEquals("2.0.1|vp5", VideoProtocol.responseToken("2.0.1", "2.0.1|vp5"));
        assertEquals("2.0.1|vp5", VideoProtocol.responseToken("2.0.1", " 2.0.1|vp5 "));
        assertEquals("2.0.1|vp2", VideoProtocol.responseToken("2.0.1", "2.0.1|vp2"));
        assertEquals("2.0.1", VideoProtocol.responseToken("2.0.1", "2.0.1"));
        assertEquals("2.0.1|vp5", VideoProtocol.responseToken("2.0.1", "2.0.2|vp5"));
    }

    @Test
    void allowsHandshakePacketsAfterClientRejection() {
        for (VideoPacketType type : VideoPacketType.values()) {
            assertEquals(
                    type == VideoPacketType.PROTOCOL_REJECT
                            || type == VideoPacketType.RESET_CLIENT
                            || type == VideoPacketType.CONFIG,
                    VideoProtocol.allowedForRejectedClient(type),
                    type.name()
            );
        }
    }

    private record CompatibilityCase(String localVersion, String remoteToken, boolean expected) {
    }
}
