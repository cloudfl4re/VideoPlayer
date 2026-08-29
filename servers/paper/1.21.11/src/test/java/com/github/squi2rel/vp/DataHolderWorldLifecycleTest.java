package com.github.squi2rel.vp;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataHolderWorldLifecycleTest {
    private static final String DIMENSION = "minecraft:overworld";

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        synchronized (DataHolder.LOCK) {
            DataHolder.areas.remove(DIMENSION);
        }
        DataHolder.unloadWorld(DIMENSION);
    }

    @Test
    void ensureWorldLoadedDoesNotReadWorldSynchronouslyWithoutAnActiveRuntime() {
        World world = mockWorld();

        assertFalse(DataHolder.isWorldTrackingReady(DIMENSION));
        DataHolder.ensureWorldLoaded(world);
        assertFalse(DataHolder.isWorldTrackingReady(DIMENSION));
        assertFalse(DataHolder.areas.containsKey(DIMENSION));
    }

    @Test
    void ensureWorldLoadedBindsAnAlreadyLoadedWorldWithoutReadingItsFile() {
        World world = mockWorld();
        synchronized (DataHolder.LOCK) {
            DataHolder.areas.put(DIMENSION, new HashMap<>());
        }

        DataHolder.ensureWorldLoaded(world);

        assertTrue(DataHolder.isWorldTrackingReady(DIMENSION));
    }

    @Test
    void unloadWorldByDimensionClearsLoadedState() {
        synchronized (DataHolder.LOCK) {
            DataHolder.areas.put(DIMENSION, new HashMap<>());
        }

        DataHolder.unloadWorld(DIMENSION);

        assertFalse(DataHolder.isWorldTrackingReady(DIMENSION));
        assertFalse(DataHolder.areas.containsKey(DIMENSION));
    }

    @Test
    void unloadWorldCancelsLoadBeforeAreaMapExists() throws Exception {
        Field field = DataHolder.class.getDeclaredField("worldLoadRequests");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Long> requests = (Map<String, Long>) field.get(null);
        synchronized (DataHolder.LOCK) {
            requests.put(DIMENSION, 17L);
        }

        DataHolder.unloadWorld(DIMENSION);

        synchronized (DataHolder.LOCK) {
            assertFalse(requests.containsKey(DIMENSION));
        }
    }

    private World mockWorld() {
        World world = mock(World.class);
        when(world.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));
        when(world.getWorldFolder()).thenReturn(tempDir.toFile());
        return world;
    }
}
