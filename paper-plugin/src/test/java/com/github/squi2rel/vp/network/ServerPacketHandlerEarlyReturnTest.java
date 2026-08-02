package com.github.squi2rel.vp.network;

import com.github.squi2rel.vp.DataHolder;
import com.github.squi2rel.vp.permission.AreaPermissionDecision;
import com.github.squi2rel.vp.permission.VideoPermissionContext;
import com.github.squi2rel.vp.permission.VideoPermissions;
import com.github.squi2rel.vp.video.IdlePlayEntry;
import com.github.squi2rel.vp.video.MetaValue;
import com.github.squi2rel.vp.video.ScreenSurface;
import com.github.squi2rel.vp.video.VideoArea;
import com.github.squi2rel.vp.video.VideoScreen;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.squi2rel.vp.network.ByteBufUtils.writeString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class ServerPacketHandlerEarlyReturnTest {
    private final UUID playerId = UUID.randomUUID();
    private final String dimension = "minecraft:overworld";
    private Player player;
    private VideoArea area;
    private VideoScreen screen;

    @TempDir
    Path worldDir;

    @BeforeEach
    void setUp() {
        World world = mock(World.class);
        when(world.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));
        when(world.getWorldFolder()).thenReturn(worldDir.toFile());

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("tester");
        when(player.getWorld()).thenReturn(world);
        when(player.isOnline()).thenReturn(true);
        when(player.isOp()).thenReturn(true);

        DataHolder.acceptHandshake(playerId);
        DataHolder.acceptHandshake(playerId);

        area = VideoArea.from(new Vector3f(), new Vector3f(16, 16, 16), "area", dimension);
        area.initServer();
        area.addPlayer(playerId);
        screen = new VideoScreen(
                area,
                "screen",
                List.of(
                        new Vector3f(0, 0, 0),
                        new Vector3f(1, 0, 0),
                        new Vector3f(1, 1, 0),
                        new Vector3f(0, 1, 0)
                ),
                ""
        );
        area.addScreen(screen);
        synchronized (DataHolder.LOCK) {
            HashMap<String, VideoArea> areas = new HashMap<>();
            areas.put(area.name, area);
            DataHolder.areas.put(dimension, areas);
        }
        DataHolder.ensureWorldLoaded(world);
    }

    @AfterEach
    void tearDown() {
        DataHolder.unloadWorld(dimension);
        DataHolder.playerLeave(playerId);
        VideoPermissions.reset();
    }

    @Test
    void staleAutoSyncConsumesClientTimeBeforeReturningError() {
        ByteBuf buf = controlled(VideoPacketType.AUTO_SYNC, "screen");
        try {
            buf.writeLong(1L);
            buf.writeLong(System.currentTimeMillis());

            ServerPacketHandler.handle(player, buf);

            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }

    @Test
    void staleSeekConsumesProgressBeforeReturningError() {
        ByteBuf buf = controlled(VideoPacketType.SEEK, "screen");
        try {
            buf.writeLong(1L);
            buf.writeLong(5000L);

            ServerPacketHandler.handle(player, buf);

            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }

    @Test
    void missingScreenSyncConsumesGenerationBeforeReturningError() {
        ByteBuf buf = controlled(VideoPacketType.SYNC, "missing");
        try {
            buf.writeLong(1L);

            ServerPacketHandler.handle(player, buf);

            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }

    @Test
    void truncatedPlayRequestIsRethrownWithoutRequestResult() {
        ByteBuf buf = controlled(VideoPacketType.REQUEST, screen.name);
        try {
            buf.writeShort(10);
            buf.writeBytes(new byte[]{1, 2});

            try (MockedStatic<DataHolder> dataHolder = mockStatic(DataHolder.class, CALLS_REAL_METHODS)) {
                assertThrows(RuntimeException.class, () -> ServerPacketHandler.handle(player, buf));
                dataHolder.verify(() -> DataHolder.sendTo(eq(player), argThat(bytes -> packetType(bytes, VideoPacketType.REQUEST_RESULT))), never());
            }
        } finally {
            buf.release();
        }
    }

    @Test
    void truncatedSeekRequestIsRethrownWithoutRequestResult() {
        ByteBuf buf = controlled(VideoPacketType.SEEK, screen.name);
        try {
            buf.writeLong(1L);

            try (MockedStatic<DataHolder> dataHolder = mockStatic(DataHolder.class, CALLS_REAL_METHODS)) {
                assertThrows(RuntimeException.class, () -> ServerPacketHandler.handle(player, buf));
                dataHolder.verify(() -> DataHolder.sendTo(eq(player), argThat(bytes -> packetType(bytes, VideoPacketType.REQUEST_RESULT))), never());
            }
        } finally {
            buf.release();
        }
    }

    @Test
    void businessExceptionAfterDecodeReturnsRequestResult() {
        when(player.isOp()).thenReturn(false);
        VideoPermissions.setAreaResolver((ignoredPlayer, ignoredAction, ignoredContext) -> {
            throw new IllegalStateException("business failure");
        });
        ByteBuf buf = controlled(VideoPacketType.REQUEST, screen.name);
        try {
            writeString(buf, "https://example.invalid/video");

            try (MockedStatic<DataHolder> dataHolder = mockStatic(DataHolder.class, CALLS_REAL_METHODS)) {
                assertDoesNotThrow(() -> ServerPacketHandler.handle(player, buf));
                dataHolder.verify(() -> DataHolder.sendTo(eq(player), argThat(bytes -> packetType(bytes, VideoPacketType.REQUEST_RESULT))), times(1));
            }
            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }

    @Test
    void deniedPlayConsumesLongUrlBeforeReturningDeniedResult() {
        denyPermissions();
        ByteBuf buf = controlled(VideoPacketType.REQUEST, screen.name);
        try {
            writeString(buf, "https://example.invalid/" + "a".repeat(283));

            try (MockedStatic<DataHolder> dataHolder = mockStatic(DataHolder.class, CALLS_REAL_METHODS)) {
                ServerPacketHandler.handle(player, buf);

                assertFalse(buf.isReadable());
                dataHolder.verify(() -> DataHolder.disconnect(eq(player), anyString()), never());
                dataHolder.verify(() -> DataHolder.sendTo(eq(player), argThat(bytes -> requestResult(
                        bytes,
                        1,
                        RequestResultStatus.DENIED,
                        true
                ))), times(1));
                dataHolder.verify(() -> DataHolder.sendTo(eq(player), argThat(bytes -> packetType(
                        bytes,
                        VideoPacketType.PERMISSIONS
                ))), times(1));
            }
        } finally {
            buf.release();
        }
    }

    @Test
    void deniedSeekDoesNotBroadcastProgress() {
        denyPermissions();
        ByteBuf buf = controlled(VideoPacketType.SEEK, screen.name);
        try {
            buf.writeLong(screen.currentPlaybackGeneration());
            buf.writeLong(5_000L);

            try (MockedStatic<DataHolder> dataHolder = mockStatic(DataHolder.class, CALLS_REAL_METHODS)) {
                ServerPacketHandler.handle(player, buf);

                assertFalse(buf.isReadable());
                dataHolder.verify(() -> DataHolder.sendTo(eq(player), argThat(bytes -> requestResult(
                        bytes,
                        1,
                        RequestResultStatus.DENIED,
                        true
                ))), times(1));
                dataHolder.verify(() -> DataHolder.sendTo(eq(player), argThat(bytes -> packetType(
                        bytes,
                        VideoPacketType.PERMISSIONS
                ))), times(1));
                dataHolder.verify(() -> DataHolder.sendTo(eq(player), argThat(bytes -> packetType(
                        bytes,
                        VideoPacketType.SYNC
                ))), never());
            }
        } finally {
            buf.release();
        }
    }

    @Test
    void missingScreenPlayConsumesUrlBeforeReturningError() {
        ByteBuf buf = controlled(VideoPacketType.REQUEST, "missing");
        try {
            writeString(buf, "https://example.invalid/video");

            ServerPacketHandler.handle(player, buf);

            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }

    @Test
    void deniedIdlePlayConsumesConfigWithoutMutatingScreen() {
        screen.setIdlePlayConfig(List.of("old"), false);
        denyPermissions();
        ByteBuf buf = controlled(VideoPacketType.IDLE_PLAY, screen.name);
        try {
            VideoPackets.writeIdlePlayMutation(buf, IdlePlayMutation.add("new-one", 10));

            ServerPacketHandler.handle(player, buf);

            assertFalse(buf.isReadable());
            assertEquals(List.of("old"), screen.idlePlayEntries.stream().map(entry -> entry.url()).toList());
            assertFalse(screen.idlePlayRandom);
        } finally {
            buf.release();
        }
    }

    @Test
    void successfulIdlePlayUsesTheServerPlayerIdentityAndPreservesItOnPriorityChange() {
        ByteBuf add = controlled(VideoPacketType.IDLE_PLAY, screen.name);
        try {
            VideoPackets.writeIdlePlayMutation(add, IdlePlayMutation.add(
                    "https://www.bilibili.com/video/BV1xx411c7mD/?vd_source=spoofed",
                    70
            ));
            ServerPacketHandler.handle(player, add);
            assertFalse(add.isReadable());
        } finally {
            add.release();
        }

        var entry = screen.idlePlayEntries.getFirst();
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD", entry.url());
        assertEquals(playerId, entry.addedBy());
        assertEquals("tester", entry.addedByName());
        assertEquals(70, entry.priority());

        ByteBuf priority = controlled(VideoPacketType.IDLE_PLAY, screen.name);
        try {
            VideoPackets.writeIdlePlayMutation(priority, IdlePlayMutation.setPriority(entry.id(), 25));
            ServerPacketHandler.handle(player, priority);
            assertFalse(priority.isReadable());
        } finally {
            priority.release();
        }

        var updated = screen.idlePlayEntries.getFirst();
        assertEquals(entry.id(), updated.id());
        assertEquals(playerId, updated.addedBy());
        assertEquals("tester", updated.addedByName());
        assertEquals(25, updated.priority());
    }

    @Test
    void adjustPriorityResolvesAgainstTheAuthoritativePriorityAndClamps() {
        ByteBuf add = controlled(VideoPacketType.IDLE_PLAY, screen.name);
        try {
            VideoPackets.writeIdlePlayMutation(add, IdlePlayMutation.add("https://example.invalid/idle", IdlePlayEntry.MAX_PRIORITY - 1));
            ServerPacketHandler.handle(player, add);
            assertFalse(add.isReadable());
        } finally {
            add.release();
        }
        var entry = screen.idlePlayEntries.getFirst();
        assertEquals(IdlePlayEntry.MAX_PRIORITY - 1, entry.priority());

        ByteBuf clamped = controlled(VideoPacketType.IDLE_PLAY, screen.name);
        try {
            VideoPackets.writeIdlePlayMutation(clamped, IdlePlayMutation.adjustPriority(entry.id(), 5));
            ServerPacketHandler.handle(player, clamped);
            assertFalse(clamped.isReadable());
        } finally {
            clamped.release();
        }
        assertEquals(IdlePlayEntry.MAX_PRIORITY, screen.idlePlayEntries.getFirst().priority());

        ByteBuf lowered = controlled(VideoPacketType.IDLE_PLAY, screen.name);
        try {
            VideoPackets.writeIdlePlayMutation(lowered, IdlePlayMutation.adjustPriority(entry.id(), -30));
            ServerPacketHandler.handle(player, lowered);
            assertFalse(lowered.isReadable());
        } finally {
            lowered.release();
        }
        assertEquals(IdlePlayEntry.MAX_PRIORITY - 30, screen.idlePlayEntries.getFirst().priority());

        ByteBuf missing = controlled(VideoPacketType.IDLE_PLAY, screen.name);
        try {
            VideoPackets.writeIdlePlayMutation(missing, IdlePlayMutation.adjustPriority(UUID.randomUUID(), 1));
            ServerPacketHandler.handle(player, missing);
            assertFalse(missing.isReadable());
        } finally {
            missing.release();
        }
        assertEquals(IdlePlayEntry.MAX_PRIORITY - 30, screen.idlePlayEntries.getFirst().priority());
    }

    @Test
    void deniedCreateAreaConsumesCandidateWithoutAddingArea() {
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);
        AtomicReference<VideoPermissionContext> checked = new AtomicReference<>();
        VideoPermissions.setAreaResolver((ignoredPlayer, ignoredAction, context) -> {
            checked.set(context);
            return AreaPermissionDecision.DENY;
        });
        ByteBuf buf = VideoPackets.create(VideoPacketType.CREATE_AREA);
        try {
            buf.writeInt(1);
            ByteBufUtils.writeVec3(buf, new Vector3f(32, 0, 32));
            ByteBufUtils.writeVec3(buf, new Vector3f(40, 8, 40));
            writeString(buf, "new-area");

            try (MockedStatic<DataHolder> dataHolder = mockStatic(DataHolder.class, CALLS_REAL_METHODS)) {
                ServerPacketHandler.handle(player, buf);

                dataHolder.verify(() -> DataHolder.sendTo(eq(player), argThat(bytes -> packetType(bytes, VideoPacketType.PERMISSIONS))), never());
            }
            assertFalse(buf.isReadable());
            assertFalse(DataHolder.areas.get(dimension).containsKey("new-area"));
            assertEquals("new-area", checked.get().areaName());
            assertEquals(new VideoPermissionContext.Position(32, 0, 32), checked.get().areaMin());
            assertEquals(new VideoPermissionContext.Position(40, 8, 40), checked.get().areaMax());
        } finally {
            buf.release();
        }
    }

    @Test
    void deniedCreateScreenConsumesCandidateWithoutAddingScreen() {
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);
        AtomicReference<VideoPermissionContext> checked = new AtomicReference<>();
        VideoPermissions.setAreaResolver((ignoredPlayer, ignoredAction, context) -> {
            checked.set(context);
            return AreaPermissionDecision.DENY;
        });
        VideoScreen candidate = new VideoScreen(
                area,
                "candidate",
                List.of(
                        new Vector3f(2, 2, 2),
                        new Vector3f(3, 2, 2),
                        new Vector3f(3, 3, 2),
                        new Vector3f(2, 3, 2)
                ),
                ""
        );
        ByteBuf buf = VideoPackets.create(VideoPacketType.CREATE_SCREEN);
        try {
            buf.writeInt(1);
            writeString(buf, area.name);
            VideoScreen.write(buf, candidate);

            try (MockedStatic<DataHolder> dataHolder = mockStatic(DataHolder.class, CALLS_REAL_METHODS)) {
                ServerPacketHandler.handle(player, buf);

                dataHolder.verify(() -> DataHolder.sendTo(eq(player), argThat(bytes -> packetType(bytes, VideoPacketType.PERMISSIONS))), never());
            }
            assertFalse(buf.isReadable());
            assertNull(area.getScreen("candidate"));
            assertEquals(1, area.screens.size());
            assertEquals("candidate", checked.get().screenName());
            assertEquals(new VideoPermissionContext.Position(2, 2, 2), checked.get().anchor());
        } finally {
            buf.release();
        }
    }

    @Test
    void updateRequiresOldAndCandidateScreenPermissionBeforeMutation() {
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);
        VideoPermissions.setAreaResolver((ignoredPlayer, ignoredAction, context) ->
                context.anchor() != null && context.anchor().x() < 1
                        ? AreaPermissionDecision.ALLOW
                        : AreaPermissionDecision.DENY);
        List<Vector3f> original = screen.vertices.stream().map(Vector3f::new).toList();
        List<Vector3f> moved = List.of(
                new Vector3f(2, 2, 2),
                new Vector3f(3, 2, 2),
                new Vector3f(3, 3, 2),
                new Vector3f(2, 3, 2)
        );
        ByteBuf buf = VideoPackets.create(VideoPacketType.UPDATE_SCREEN);
        try {
            buf.writeInt(1);
            writeString(buf, area.name);
            writeString(buf, screen.name);
            buf.writeByte(moved.size());
            for (Vector3f vertex : moved) ByteBufUtils.writeVec3(buf, vertex);
            writeString(buf, "");
            VideoScreen.writeDisplayConfig(buf, screen);

            try (MockedStatic<DataHolder> dataHolder = mockStatic(DataHolder.class, CALLS_REAL_METHODS)) {
                ServerPacketHandler.handle(player, buf);

                dataHolder.verify(() -> DataHolder.sendTo(eq(player), argThat(bytes -> packetType(bytes, VideoPacketType.PERMISSIONS))), never());
            }
            assertFalse(buf.isReadable());
            assertEquals(original, screen.vertices);
        } finally {
            buf.release();
        }
    }

    @Test
    void deniedMetadataConsumesValueWithoutMutatingScreen() {
        denyPermissions();
        ByteBuf buf = controlled(VideoPacketType.SET_SCREEN_METADATA, screen.name);
        try {
            writeString(buf, "mute");
            buf.writeBoolean(false);
            VideoPackets.writeMetaValue(buf, MetaValue.ofBool(true));

            ServerPacketHandler.handle(player, buf);

            assertFalse(buf.isReadable());
            assertFalse(screen.metadata.getBool("mute", false));
        } finally {
            buf.release();
        }
    }

    @Test
    void successfulUpdateRefreshesPermissionContexts() {
        List<Vector3f> moved = List.of(
                new Vector3f(2, 2, 2),
                new Vector3f(3, 2, 2),
                new Vector3f(3, 3, 2),
                new Vector3f(2, 3, 2)
        );
        ByteBuf buf = VideoPackets.create(VideoPacketType.UPDATE_SCREEN);
        try {
            buf.writeInt(1);
            writeString(buf, area.name);
            writeString(buf, screen.name);
            buf.writeByte(moved.size());
            for (Vector3f vertex : moved) ByteBufUtils.writeVec3(buf, vertex);
            writeString(buf, "");
            VideoScreen.writeDisplayConfig(buf, screen);

            try (MockedStatic<ServerPacketHandler> handler = mockStatic(ServerPacketHandler.class, CALLS_REAL_METHODS)) {
                ServerPacketHandler.handle(player, buf);

                handler.verify(() -> ServerPacketHandler.refreshPermissions(area), times(1));
            }
            assertEquals(moved, screen.vertices);
        } finally {
            buf.release();
        }
    }

    @Test
    void createScreenBroadcastsCurvedStripVerticesWithoutChangingTheirOrder() {
        List<Vector3f> vertices = curvedStrip();
        VideoScreen candidate = new VideoScreen(area, "curved", vertices, "");
        ByteBuf buf = VideoPackets.create(VideoPacketType.CREATE_SCREEN);
        try {
            buf.writeInt(1);
            writeString(buf, area.name);
            VideoScreen.write(buf, candidate);

            try (MockedStatic<DataHolder> dataHolder = mockStatic(DataHolder.class, CALLS_REAL_METHODS)) {
                ServerPacketHandler.handle(player, buf);

                dataHolder.verify(() -> DataHolder.sendTo(eq(playerId), argThat(bytes -> screenPacket(
                        bytes, VideoPacketType.CREATE_SCREEN, area.name, candidate.name, vertices
                ))), times(1));
            }
            assertFalse(buf.isReadable());
            VideoScreen stored = area.getScreen(candidate.name);
            assertEquals(ScreenSurface.FLAT, stored.surface);
            assertEquals(vertices, stored.vertices);
        } finally {
            buf.release();
        }
    }

    @Test
    void updateScreenBroadcastsCurvedStripVerticesWithoutChangingTheirOrder() {
        List<Vector3f> vertices = curvedStrip();
        VideoScreen displayConfig = new VideoScreen(area, screen.name, vertices, "");
        ByteBuf buf = VideoPackets.create(VideoPacketType.UPDATE_SCREEN);
        try {
            buf.writeInt(1);
            writeString(buf, area.name);
            writeString(buf, screen.name);
            buf.writeByte(vertices.size());
            for (Vector3f vertex : vertices) ByteBufUtils.writeVec3(buf, vertex);
            writeString(buf, "");
            VideoScreen.writeDisplayConfig(buf, displayConfig);

            try (MockedStatic<DataHolder> dataHolder = mockStatic(DataHolder.class, CALLS_REAL_METHODS)) {
                ServerPacketHandler.handle(player, buf);

                dataHolder.verify(() -> DataHolder.sendTo(eq(playerId), argThat(bytes -> screenPacket(
                        bytes, VideoPacketType.UPDATE_SCREEN, area.name, screen.name, vertices
                ))), times(1));
            }
            assertFalse(buf.isReadable());
            assertEquals(ScreenSurface.FLAT, screen.surface);
            assertEquals(vertices, screen.vertices);
        } finally {
            buf.release();
        }
    }

    @Test
    void unexpectedTrailingByteRemainsForStrictPacketCheck() {
        ByteBuf buf = controlled(VideoPacketType.SYNC, "missing");
        try {
            buf.writeLong(1L);
            buf.writeByte(99);

            try (MockedStatic<DataHolder> dataHolder = mockStatic(DataHolder.class, CALLS_REAL_METHODS)) {
                ServerPacketHandler.handle(player, buf);

                assertEquals(1, buf.readableBytes());
                assertTrue(buf.isReadable());
                dataHolder.verify(() -> DataHolder.disconnect(eq(player), eq("Illegal packet! Remaining: 1")), times(1));
            }
        } finally {
            buf.release();
        }
    }

    private void denyPermissions() {
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);
        VideoPermissions.setAreaResolver((ignoredPlayer, ignoredAction, ignoredContext) -> AreaPermissionDecision.DENY);
    }

    private ByteBuf controlled(VideoPacketType type, String screenName) {
        ByteBuf buf = VideoPackets.create(type);
        buf.writeInt(1);
        writeString(buf, area.name);
        writeString(buf, screenName);
        return buf;
    }

    private static List<Vector3f> curvedStrip() {
        ArrayList<Vector3f> vertices = new ArrayList<>();
        for (int degree : new int[]{-60, -30, 0, 30, 60}) {
            double radians = Math.toRadians(degree);
            vertices.add(new Vector3f(
                    8.0f + (float) (4.0 * Math.sin(radians)),
                    7.0f,
                    4.0f + (float) (4.0 * (1.0 - Math.cos(radians)))
            ));
        }
        for (int degree : new int[]{60, 30, 0, -30, -60}) {
            double radians = Math.toRadians(degree);
            vertices.add(new Vector3f(
                    8.0f + (float) (4.0 * Math.sin(radians)),
                    4.0f,
                    4.0f + (float) (4.0 * (1.0 - Math.cos(radians)))
            ));
        }
        return vertices;
    }

    private static boolean screenPacket(byte[] bytes, VideoPacketType type, String areaName, String screenName,
                                        List<Vector3f> expectedVertices) {
        if (bytes == null) return false;
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        try {
            if (VideoPackets.readType(buf) != type) return false;
            if (!areaName.equals(VideoPackets.readName(buf))) return false;

            VideoScreen decoded;
            if (type == VideoPacketType.CREATE_SCREEN) {
                if (buf.readUnsignedByte() != 1) return false;
                decoded = VideoScreen.read(buf, null);
                VideoPackets.readUv(buf, decoded);
                VideoPackets.readScale(buf, decoded);
            } else if (type == VideoPacketType.UPDATE_SCREEN) {
                String decodedName = VideoPackets.readName(buf);
                int count = buf.readUnsignedByte();
                ArrayList<Vector3f> vertices = new ArrayList<>(count);
                for (int i = 0; i < count; i++) vertices.add(ByteBufUtils.readVec3(buf));
                String source = VideoPackets.readName(buf);
                decoded = new VideoScreen(null, decodedName, vertices, source);
                VideoScreen.readDisplayConfig(buf, decoded);
            } else {
                return false;
            }
            return screenName.equals(decoded.name)
                    && decoded.surface == ScreenSurface.FLAT
                    && expectedVertices.equals(decoded.vertices)
                    && !buf.isReadable();
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            buf.release();
        }
    }

    private static boolean packetType(byte[] bytes, VideoPacketType expected) {
        if (bytes == null) return false;
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        try {
            return VideoPackets.readType(buf) == expected;
        } finally {
            buf.release();
        }
    }

    private static boolean requestResult(byte[] bytes, int expectedId, RequestResultStatus expectedStatus, boolean messageRequired) {
        if (bytes == null) return false;
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        try {
            if (VideoPackets.readType(buf) != VideoPacketType.REQUEST_RESULT) return false;
            int requestId = buf.readInt();
            RequestResultStatus status = RequestResultStatus.fromId(buf.readUnsignedByte());
            var message = VideoPackets.readTranslation(buf);
            return requestId == expectedId
                    && status == expectedStatus
                    && (!messageRequired || message != null && !message.isEmpty())
                    && !buf.isReadable();
        } finally {
            buf.release();
        }
    }
}
