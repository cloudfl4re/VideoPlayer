package com.github.squi2rel.vp.video;

import com.github.squi2rel.vp.provider.VideoInfo;
import com.google.gson.Gson;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


class VideoScreenPlaylistPersistenceTest {
    @Test
    void restoresPersistedPlaylistOrderAndResumeProgress() {
        VideoInfo first = info("first", "https://example.com/first.mp4");
        VideoInfo second = info("second", "https://example.com/second.mp4");
        VideoArea area = new VideoArea(new Vector3f(), new Vector3f(2), "area", "world");
        VideoScreen screen = screen(area);
        screen.playlist = new ArrayList<>(List.of(first, second));
        screen.playbackResumeProgress = 12_345L;

        VideoScreen restored = new Gson().fromJson(new Gson().toJson(screen), VideoScreen.class);
        restored.area = area;
        restored.initServer();

        assertEquals(first.name(), restored.currentPlaying().name());
        assertEquals(2, restored.queueSize());
        restored.prepareForPersistence();
        assertEquals(List.of(first.name(), second.name()), restored.playlist.stream().map(VideoInfo::name).toList());
        assertEquals(12_345L, restored.playbackResumeProgress);
    }

    private static VideoScreen screen(VideoArea area) {
        return new VideoScreen(area, "screen", new Vector3f(), new Vector3f(1, 0, 0),
                new Vector3f(1, 1, 0), new Vector3f(0, 1, 0), "");
    }

    private static VideoInfo info(String name, String path) {
        return new VideoInfo("player", name, path, "", -1L, true, new String[0], 30_000L);
    }
}
