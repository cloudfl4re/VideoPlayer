package com.github.squi2rel.vp;

import com.github.squi2rel.vp.video.VideoArea;
import com.github.squi2rel.vp.video.VideoScreen;
import com.google.gson.Gson;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataHolderPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void olderAsyncSnapshotCannotOverwriteNewerSavedGeneration() throws Exception {
        Path path = tempDir.resolve("videoplayer.json");
        WorldSaveQueue.Snapshot newer = snapshot(path, 2L, "newer");
        WorldSaveQueue.Snapshot older = snapshot(path, 1L, "older");

        assertTrue(DataHolder.writeWorldSnapshot(newer));
        assertFalse(DataHolder.writeWorldSnapshot(older));
        assertEquals("{\"saveGeneration\":2,\"marker\":\"newer\"}", Files.readString(path));
    }

    @Test
    void worldFileLockAcquisitionIsBounded() throws Exception {
        Path path = tempDir.resolve("videoplayer.json");
        Path lockPath = tempDir.resolve("videoplayer.json.lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                    assertThrows(IllegalStateException.class, () -> DataHolder.writeWorldSnapshot(
                            snapshot(path, 1L, "blocked"),
                            25L
                    ))
            );
        }
    }

    @Test
    void worldSnapshotRoundTripPreservesCurvedStripVertices() {
        List<Vector3f> expectedVertices = curvedStrip();
        VideoArea area = VideoArea.from(
                new Vector3f(-16.0f, 0.0f, -16.0f),
                new Vector3f(16.0f, 16.0f, 16.0f),
                "area",
                "minecraft:overworld"
        );
        VideoScreen screen = new VideoScreen(area, "curved", expectedVertices, "");
        area.addScreen(screen);

        WorldConfigSnapshot snapshot = WorldConfigSnapshot.capture(new ServerConfig(), List.of(area), 3L);
        screen.vertices.getFirst().set(100.0f, 100.0f, 100.0f);

        ServerConfig restored = new Gson().fromJson(snapshot.serialize(new Gson()), ServerConfig.class);
        DataHolder.validateConfig(restored);
        VideoScreen restoredScreen = restored.areas.getFirst().screens.getFirst();
        assertEquals(expectedVertices, restoredScreen.vertices);
    }

    private static WorldSaveQueue.Snapshot snapshot(Path path, long generation, String marker) {
        return new WorldSaveQueue.Snapshot(
                "minecraft:overworld",
                path,
                1L,
                1L,
                generation,
                generation,
                false,
                "{\"saveGeneration\":" + generation + ",\"marker\":\"" + marker + "\"}"
        );
    }

    private static List<Vector3f> curvedStrip() {
        ArrayList<Vector3f> vertices = new ArrayList<>();
        for (int degree : new int[]{-60, -30, 0, 30, 60}) {
            double radians = Math.toRadians(degree);
            vertices.add(new Vector3f(
                    (float) (4.0 * Math.sin(radians)),
                    7.0f,
                    (float) (4.0 * (1.0 - Math.cos(radians)))
            ));
        }
        for (int degree : new int[]{60, 30, 0, -30, -60}) {
            double radians = Math.toRadians(degree);
            vertices.add(new Vector3f(
                    (float) (4.0 * Math.sin(radians)),
                    4.0f,
                    (float) (4.0 * (1.0 - Math.cos(radians)))
            ));
        }
        return vertices;
    }
}
