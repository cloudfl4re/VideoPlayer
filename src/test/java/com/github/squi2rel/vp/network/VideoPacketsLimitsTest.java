package com.github.squi2rel.vp.network;

import com.github.squi2rel.vp.provider.VideoInfo;
import com.github.squi2rel.vp.video.PlaybackQueue;
import com.github.squi2rel.vp.video.VideoArea;
import com.github.squi2rel.vp.video.IdlePlayEntry;
import com.github.squi2rel.vp.video.VideoScreen;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoPacketsLimitsTest {
    @Test
    void acceptsPayloadAtThirtyThousandBytesAndRejectsTheNextByte() {
        ByteBuf accepted = Unpooled.buffer(VideoPackets.MAX_PAYLOAD_BYTES);
        accepted.writeZero(VideoPackets.MAX_PAYLOAD_BYTES);
        assertEquals(VideoPackets.MAX_PAYLOAD_BYTES, VideoPackets.toByteArray(accepted).length);
        assertEquals(0, accepted.refCnt());

        ByteBuf rejected = Unpooled.buffer(VideoPackets.MAX_PAYLOAD_BYTES + 1);
        rejected.writeZero(VideoPackets.MAX_PAYLOAD_BYTES + 1);
        assertThrows(IllegalStateException.class, () -> VideoPackets.toByteArray(rejected));
        assertEquals(0, rejected.refCnt());
    }

    @Test
    void acceptsThirtyTwoQueueItemsAndRejectsTheThirtyThird() {
        PlaybackQueue queue = new PlaybackQueue(screen());
        for (int i = 0; i < PlaybackQueue.MAX_ITEMS; i++) {
            assertTrue(queue.add(info("video-" + i)));
        }

        assertEquals(PlaybackQueue.MAX_ITEMS, queue.size());
        assertFalse(queue.add(info("video-32")));
        assertEquals(PlaybackQueue.MAX_ITEMS, queue.size());
    }

    @Test
    void rejectsAPlaylistSnapshotThatContainsThirtyThreeItems() {
        VideoScreen screen = screen();
        for (int i = 0; i < PlaybackQueue.MAX_ITEMS; i++) {
            screen.infos.add(info("video-" + i));
        }

        byte[] accepted = VideoPackets.updatePlaylist(List.of(screen));
        assertTrue(accepted.length <= VideoPackets.MAX_PAYLOAD_BYTES);

        screen.infos.add(info("video-32"));
        assertThrows(IllegalStateException.class, () -> VideoPackets.updatePlaylist(List.of(screen)));
    }

    @Test
    void appliesParameterCountAndUtf8ByteLimits() {
        String utf8Parameter = "界".repeat(85) + "x";
        assertEquals(256, utf8Parameter.getBytes(StandardCharsets.UTF_8).length);
        String[] accepted = new String[VideoInfo.MAX_PARAMS];
        Arrays.fill(accepted, utf8Parameter);

        VideoInfo info = new VideoInfo("", "", "", "", -1, true, accepted, 0);
        assertEquals(VideoInfo.MAX_TOTAL_PARAM_BYTES, Arrays.stream(info.params())
                .mapToInt(value -> value.getBytes(StandardCharsets.UTF_8).length)
                .sum());

        String[] tooMany = new String[VideoInfo.MAX_PARAMS + 1];
        assertThrows(IllegalArgumentException.class,
                () -> new VideoInfo("", "", "", "", -1, true, tooMany, 0));

        String[] tooLarge = accepted.clone();
        tooLarge[0] += "x";
        assertThrows(IllegalArgumentException.class,
                () -> new VideoInfo("", "", "", "", -1, true, tooLarge, 0));
    }

    @Test
    void truncatesProviderNamesAtUtf8CodePointBoundaries() {
        VideoInfo info = new VideoInfo("player", "界".repeat(100), "", "", -1, true, new String[0], 0);

        assertTrue(info.name().getBytes(StandardCharsets.UTF_8).length <= 256);
        assertFalse(info.name().endsWith("�"));
    }

    @Test
    void requestCarriesPlaybackGenerationAndAuthoritativeProgress() {
        VideoScreen screen = screen();
        VideoInfo info = info("video");
        ByteBuf buf = Unpooled.wrappedBuffer(VideoPackets.request(screen, info, false, 17L, 2_500L));
        try {
            assertEquals(VideoPacketType.REQUEST, VideoPackets.readType(buf));
            assertEquals("area", VideoPackets.readName(buf));
            assertEquals("screen", VideoPackets.readName(buf));
            assertEquals(17L, buf.readLong());
            assertEquals(2_500L, buf.readLong());
            assertTrue(buf.readLong() > 0L);
            VideoInfo decoded = VideoInfo.read(buf);
            assertEquals(info.playerName(), decoded.playerName());
            assertEquals(info.name(), decoded.name());
            assertEquals(info.path(), decoded.path());
            assertArrayEquals(info.params(), decoded.params());
            assertFalse(buf.readBoolean());
            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }

    @Test
    void acceptsVideoInfoAtEncodedLimitAndRejectsTheNextByte() {
        VideoInfo accepted = sizedInfo(7_076);
        ByteBuf buf = Unpooled.buffer(VideoInfo.MAX_ENCODED_BYTES);
        try {
            VideoInfo.write(buf, accepted);
            assertEquals(VideoInfo.MAX_ENCODED_BYTES, buf.readableBytes());
            VideoInfo decoded = VideoInfo.read(buf);
            assertEquals(accepted.playerName(), decoded.playerName());
            assertEquals(accepted.name(), decoded.name());
            assertEquals(accepted.path(), decoded.path());
            assertEquals(accepted.rawPath(), decoded.rawPath());
            assertArrayEquals(accepted.params(), decoded.params());
            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }

        assertThrows(IllegalArgumentException.class, () -> sizedInfo(7_077));
    }

    @Test
    void appliesIdlePlayUtf8TotalByteLimitWhenWritingAndReading() {
        List<String> acceptedUrls = idleUrls(VideoScreen.MAX_IDLE_PLAY_TOTAL_BYTES);
        assertEquals(VideoScreen.MAX_IDLE_PLAY_TOTAL_BYTES, utf8Bytes(acceptedUrls));

        VideoScreen source = screen();
        source.setIdlePlayConfig(acceptedUrls, true);
        ByteBuf accepted = Unpooled.buffer();
        try {
            VideoPackets.writeIdlePlayConfig(accepted, source);
            VideoScreen decoded = screen();
            VideoPackets.readIdlePlayConfig(accepted, decoded);
            assertEquals(acceptedUrls, idlePlayUrls(decoded));
            assertTrue(decoded.idlePlayRandom);
            assertFalse(accepted.isReadable());
        } finally {
            accepted.release();
        }

        List<String> rejectedUrls = idleUrls(VideoScreen.MAX_IDLE_PLAY_TOTAL_BYTES + 1);
        VideoScreen rejectedSource = screen();
        assertThrows(IllegalArgumentException.class, () -> rejectedSource.setIdlePlayConfig(rejectedUrls, false));
        rejectedSource.idlePlayEntries = new ArrayList<>();
        for (String url : rejectedUrls) rejectedSource.idlePlayEntries.add(IdlePlayEntry.legacy(url));
        ByteBuf rejectedWrite = Unpooled.buffer();
        try {
            assertThrows(IllegalStateException.class,
                    () -> VideoPackets.writeIdlePlayConfig(rejectedWrite, rejectedSource));
        } finally {
            rejectedWrite.release();
        }

        ByteBuf rejectedRead = Unpooled.buffer();
        try {
            rejectedRead.writeBoolean(false);
            rejectedRead.writeByte(rejectedUrls.size());
            for (String url : rejectedUrls) {
                writeRawIdlePlayEntry(rejectedRead, url, 0);
            }
            assertThrows(IllegalStateException.class,
                    () -> VideoPackets.readIdlePlayConfig(rejectedRead, screen()));
        } finally {
            rejectedRead.release();
        }
    }

    @Test
    void appliesIdlePlayPerUrlUtf8ByteLimitForNonAsciiValues() {
        String cjk = "\u754C";
        String acceptedUrl = cjk.repeat(341) + "a";
        String rejectedUrl = acceptedUrl + cjk;
        assertEquals(VideoScreen.MAX_IDLE_PLAY_URL_BYTES, ByteBufUtils.utf8Length(acceptedUrl));
        assertEquals(VideoScreen.MAX_IDLE_PLAY_URL_BYTES + 3, ByteBufUtils.utf8Length(rejectedUrl));

        VideoScreen acceptedSource = screen();
        acceptedSource.setIdlePlayConfig(List.of(acceptedUrl), false);
        ByteBuf accepted = Unpooled.buffer();
        try {
            VideoPackets.writeIdlePlayConfig(accepted, acceptedSource);
            VideoScreen decoded = screen();
            VideoPackets.readIdlePlayConfig(accepted, decoded);
            assertEquals(List.of(acceptedUrl), idlePlayUrls(decoded));
        } finally {
            accepted.release();
        }

        VideoScreen rejectedSource = screen();
        assertThrows(IllegalArgumentException.class, () -> rejectedSource.setIdlePlayConfig(List.of(rejectedUrl), false));
        rejectedSource.idlePlayEntries = new ArrayList<>(List.of(IdlePlayEntry.legacy(rejectedUrl)));
        ByteBuf rejectedWrite = Unpooled.buffer();
        try {
            assertThrows(IllegalStateException.class, () -> VideoPackets.writeIdlePlayConfig(rejectedWrite, rejectedSource));
        } finally {
            rejectedWrite.release();
        }

        ByteBuf rejectedRead = Unpooled.buffer();
        try {
            rejectedRead.writeBoolean(false);
            rejectedRead.writeByte(1);
            writeRawIdlePlayEntry(rejectedRead, rejectedUrl, 0);
            assertThrows(IllegalStateException.class, () -> VideoPackets.readIdlePlayConfig(rejectedRead, screen()));
        } finally {
            rejectedRead.release();
        }
    }

    @Test
    void idlePlayRequestSerializationDoesNotMutateCurrentScreenState() {
        VideoScreen current = screen();
        current.setIdlePlayConfig(List.of("server-state"), false);
        ByteBuf first = Unpooled.buffer();
        ByteBuf second = Unpooled.buffer();
        try {
            VideoPackets.writeIdlePlayConfig(first, List.of("request-one"), true);
            VideoPackets.writeIdlePlayConfig(second, List.of("request-two"), false);

            assertEquals(List.of("server-state"), idlePlayUrls(current));
            assertFalse(current.idlePlayRandom);

            VideoScreen decodedFirst = screen();
            VideoPackets.readIdlePlayConfig(first, decodedFirst);
            assertEquals(List.of("request-one"), idlePlayUrls(decodedFirst));
            assertTrue(decodedFirst.idlePlayRandom);

            VideoScreen decodedSecond = screen();
            VideoPackets.readIdlePlayConfig(second, decodedSecond);
            assertEquals(List.of("request-two"), idlePlayUrls(decodedSecond));
            assertFalse(decodedSecond.idlePlayRandom);

            assertEquals(List.of("server-state"), idlePlayUrls(current));
            assertFalse(current.idlePlayRandom);
        } finally {
            first.release();
            second.release();
        }
    }

    @Test
    void rejectsDuplicateEntryIdsAndInvalidSnapshotPriorities() {
        UUID duplicate = UUID.randomUUID();
        ByteBuf duplicateIds = Unpooled.buffer();
        try {
            duplicateIds.writeBoolean(false);
            duplicateIds.writeByte(2);
            writeRawIdlePlayEntry(duplicateIds, duplicate, "first", 0);
            writeRawIdlePlayEntry(duplicateIds, duplicate, "second", 0);
            assertThrows(IllegalStateException.class, () -> VideoPackets.readIdlePlayConfig(duplicateIds, screen()));
        } finally {
            duplicateIds.release();
        }

        ByteBuf invalidPriority = Unpooled.buffer();
        try {
            invalidPriority.writeBoolean(false);
            invalidPriority.writeByte(1);
            writeRawIdlePlayEntry(invalidPriority, UUID.randomUUID(), "entry", 101);
            assertThrows(IllegalStateException.class, () -> VideoPackets.readIdlePlayConfig(invalidPriority, screen()));
        } finally {
            invalidPriority.release();
        }
    }

    @Test
    void addIdlePlayEntryReturnsFalseInsteadOfThrowingWhenLimitsAreExceeded() {
        VideoScreen screen = screen();
        assertFalse(screen.addIdlePlayEntry("x".repeat(VideoScreen.MAX_IDLE_PLAY_URL_BYTES + 1), UUID.randomUUID(), "player", 0));
        assertTrue(screen.idlePlayEntries.isEmpty());

        String block = "x".repeat(VideoScreen.MAX_IDLE_PLAY_URL_BYTES);
        int blocks = VideoScreen.MAX_IDLE_PLAY_TOTAL_BYTES / VideoScreen.MAX_IDLE_PLAY_URL_BYTES;
        for (int i = 0; i < blocks; i++) {
            assertTrue(screen.addIdlePlayEntry(block, UUID.randomUUID(), "player", 0));
        }
        int remaining = VideoScreen.MAX_IDLE_PLAY_TOTAL_BYTES - blocks * VideoScreen.MAX_IDLE_PLAY_URL_BYTES;
        assertFalse(screen.addIdlePlayEntry("x".repeat(remaining + 1), UUID.randomUUID(), "player", 0));
        assertEquals(blocks, screen.idlePlayEntries.size());
        assertTrue(screen.addIdlePlayEntry("x".repeat(remaining), UUID.randomUUID(), "player", 0));
        assertFalse(screen.addIdlePlayEntry("y", UUID.randomUUID(), "player", 0));
        assertEquals(blocks + 1, screen.idlePlayEntries.size());

        VideoScreen counted = screen();
        for (int i = 0; i < VideoScreen.MAX_IDLE_PLAY_ITEMS; i++) {
            assertTrue(counted.addIdlePlayEntry("u" + i, UUID.randomUUID(), "player", 0));
        }
        assertFalse(counted.addIdlePlayEntry("overflow", UUID.randomUUID(), "player", 0));
        assertEquals(VideoScreen.MAX_IDLE_PLAY_ITEMS, counted.idlePlayEntries.size());
    }

    @Test
    void roundTripsAdjustPriorityMutationsWithinDeltaBounds() {
        UUID id = UUID.randomUUID();
        int range = IdlePlayEntry.MAX_PRIORITY - IdlePlayEntry.MIN_PRIORITY;
        for (int delta : new int[]{-range, -1, 1, range}) {
            ByteBuf buf = Unpooled.buffer();
            try {
                VideoPackets.writeIdlePlayMutation(buf, IdlePlayMutation.adjustPriority(id, delta));
                IdlePlayMutation decoded = VideoPackets.readIdlePlayMutation(buf);
                assertEquals(IdlePlayAction.ADJUST_PRIORITY, decoded.action());
                assertEquals(id, decoded.entryId());
                assertEquals(delta, decoded.delta());
                assertFalse(buf.isReadable());
            } finally {
                buf.release();
            }
        }
    }

    @Test
    void rejectsAdjustPriorityDeltasOfZeroOrBeyondThePriorityRange() {
        UUID id = UUID.randomUUID();
        int range = IdlePlayEntry.MAX_PRIORITY - IdlePlayEntry.MIN_PRIORITY;
        for (int delta : new int[]{0, range + 1, -(range + 1)}) {
            ByteBuf rejectedWrite = Unpooled.buffer();
            try {
                assertThrows(IllegalArgumentException.class,
                        () -> VideoPackets.writeIdlePlayMutation(rejectedWrite, IdlePlayMutation.adjustPriority(id, delta)));
            } finally {
                rejectedWrite.release();
            }
            ByteBuf rejectedRead = Unpooled.buffer();
            try {
                rejectedRead.writeByte(IdlePlayAction.ADJUST_PRIORITY.id());
                VideoPackets.writeUuid(rejectedRead, id);
                rejectedRead.writeByte(delta);
                assertThrows(IllegalStateException.class, () -> VideoPackets.readIdlePlayMutation(rejectedRead));
            } finally {
                rejectedRead.release();
            }
        }
    }

    @Test
    void roundTripsAuthoritativeIdlePlayEntries() {
        VideoScreen source = screen();
        List<IdlePlayEntry> expected = List.of(
                new IdlePlayEntry(UUID.randomUUID(), "first", UUID.randomUUID(), "player-one", 100),
                new IdlePlayEntry(UUID.randomUUID(), "first", UUID.randomUUID(), "player-two", 25)
        );
        source.setIdlePlayEntries(expected, true);
        ByteBuf buf = Unpooled.buffer();
        try {
            VideoPackets.writeIdlePlayConfig(buf, source);
            VideoScreen decoded = screen();
            VideoPackets.readIdlePlayConfig(buf, decoded);

            assertEquals(expected, decoded.idlePlayEntries);
            assertTrue(decoded.idlePlayRandom);
            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }

    private static VideoInfo sizedInfo(int parameterBytes) {
        return new VideoInfo(
                "p".repeat(256),
                "n".repeat(256),
                "u".repeat(8_192),
                "r".repeat(8_192),
                -1,
                true,
                new String[]{"x".repeat(parameterBytes)},
                0
        );
    }

    private static VideoInfo info(String name) {
        return new VideoInfo("player", name, "https://example.com/video", "", -1, true, new String[0], 1_000);
    }

    private static VideoScreen screen() {
        VideoArea area = new VideoArea(new Vector3f(), new Vector3f(1), "area", "world");
        return new VideoScreen(
                area,
                "screen",
                new Vector3f(),
                new Vector3f(1, 0, 0),
                new Vector3f(1, 1, 0),
                new Vector3f(0, 1, 0),
                ""
        );
    }

    private static List<String> idleUrls(int totalBytes) {
        ArrayList<String> urls = new ArrayList<>();
        int remaining = totalBytes;
        while (remaining >= 999) {
            urls.add("界".repeat(333));
            remaining -= 999;
        }
        if (remaining > 0) urls.add("x".repeat(remaining));
        return urls;
    }

    private static int utf8Bytes(List<String> values) {
        return values.stream().mapToInt(value -> value.getBytes(StandardCharsets.UTF_8).length).sum();
    }

    private static List<String> idlePlayUrls(VideoScreen screen) {
        return screen.idlePlayEntries.stream().map(IdlePlayEntry::url).toList();
    }

    private static void writeRawIdlePlayEntry(ByteBuf buf, String url, int priority) {
        writeRawIdlePlayEntry(buf, UUID.randomUUID(), url, priority);
    }

    private static void writeRawIdlePlayEntry(ByteBuf buf, UUID id, String url, int priority) {
        VideoPackets.writeUuid(buf, id);
        ByteBufUtils.writeString(buf, url);
        VideoPackets.writeUuid(buf, IdlePlayEntry.UNKNOWN_UUID);
        ByteBufUtils.writeString(buf, "");
        buf.writeByte(priority);
    }
}
