package com.github.squi2rel.vp;

import com.github.squi2rel.vp.provider.VideoInfo;
import com.github.squi2rel.vp.video.MetaValue;
import com.github.squi2rel.vp.video.IdlePlayEntry;
import com.github.squi2rel.vp.video.VideoArea;
import com.github.squi2rel.vp.video.VideoScreen;
import com.google.gson.Gson;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldConfigSnapshotTest {
    @Test
    void captureDoesNotObserveLaterWorldMutations() {
        ServerConfig shared = new ServerConfig();
        VideoArea area = new VideoArea(new Vector3f(1, 2, 3), new Vector3f(4, 5, 6), "area", "minecraft:overworld");
        VideoScreen screen = new VideoScreen(area, "screen", List.of(
                new Vector3f(0, 0, 0),
                new Vector3f(1, 0, 0),
                new Vector3f(1, 1, 0),
                new Vector3f(0, 1, 0)
        ), "source");
        screen.idlePlayEntries.add(IdlePlayEntry.create("https://example.invalid/first", UUID.randomUUID(), "player", 75));
        screen.metadata.set("volume", MetaValue.ofInt(75));
        screen.playlist.add(new VideoInfo("player", "title", "https://example.invalid/video", "", -1L, true, new String[]{"referrer=test"}, 1000L));
        area.addScreen(screen);

        WorldConfigSnapshot snapshot = WorldConfigSnapshot.capture(shared, List.of(area), 7L);
        area.name = "changed";
        screen.idlePlayEntries.set(0, IdlePlayEntry.legacy("https://example.invalid/changed"));
        screen.metadata.set("volume", MetaValue.ofInt(5));
        screen.playlist.clear();

        ServerConfig restored = new Gson().fromJson(snapshot.serialize(new Gson()), ServerConfig.class);
        VideoScreen restoredScreen = restored.areas.getFirst().screens.getFirst();
        assertEquals(7L, restored.saveGeneration);
        assertEquals("area", restored.areas.getFirst().name);
        assertEquals("https://example.invalid/first", restoredScreen.idlePlayEntries.getFirst().url());
        assertEquals("player", restoredScreen.idlePlayEntries.getFirst().addedByName());
        assertEquals(75, restoredScreen.idlePlayEntries.getFirst().priority());
        assertEquals(75, restoredScreen.metadata.getInt("volume", 0));
        assertEquals(1, restoredScreen.playlist.size());
    }
}
