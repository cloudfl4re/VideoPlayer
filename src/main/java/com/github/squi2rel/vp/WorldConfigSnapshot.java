package com.github.squi2rel.vp;

import com.github.squi2rel.vp.provider.VideoInfo;
import com.github.squi2rel.vp.video.MetaValue;
import com.github.squi2rel.vp.video.IdlePlayEntry;
import com.github.squi2rel.vp.video.ScreenMetadata;
import com.github.squi2rel.vp.video.VideoArea;
import com.github.squi2rel.vp.video.VideoScreen;
import com.google.gson.Gson;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

final class WorldConfigSnapshot {
    private final ServerConfig config;

    private WorldConfigSnapshot(ServerConfig config) {
        this.config = config;
    }

    static WorldConfigSnapshot capture(ServerConfig shared, Collection<VideoArea> areas, long saveGeneration) {
        ServerConfig copy = new ServerConfig();
        copy.saveGeneration = saveGeneration;
        copy.remoteControlName = shared.remoteControlName;
        copy.remoteControlId = shared.remoteControlId;
        copy.remoteControlRange = shared.remoteControlRange;
        copy.noControlRange = shared.noControlRange;
        copy.areas = new ArrayList<>();
        if (areas != null) {
            for (VideoArea area : areas) {
                if (area != null) copy.areas.add(copyArea(area));
            }
        }
        return new WorldConfigSnapshot(copy);
    }

    String serialize(Gson gson) {
        return gson.toJson(config);
    }

    private static VideoArea copyArea(VideoArea source) {
        Vector3f min = source.min == null ? new Vector3f() : new Vector3f(source.min);
        Vector3f max = source.max == null ? new Vector3f() : new Vector3f(source.max);
        VideoArea copy = new VideoArea(min, max, source.name, source.dim);
        copy.screens = new ArrayList<>();
        if (source.screens != null) {
            for (VideoScreen screen : source.screens) {
                if (screen == null) continue;
                screen.prepareForPersistence();
                copy.addScreen(copyScreen(copy, screen));
            }
        }
        return copy;
    }

    private static VideoScreen copyScreen(VideoArea area, VideoScreen source) {
        ArrayList<Vector3f> vertices = new ArrayList<>();
        if (source.vertices != null) {
            for (Vector3f vertex : source.vertices) {
                if (vertex != null) vertices.add(new Vector3f(vertex));
            }
        }
        VideoScreen copy = new VideoScreen(area, source.name, vertices, source.source);
        copy.u1 = source.u1;
        copy.v1 = source.v1;
        copy.u2 = source.u2;
        copy.v2 = source.v2;
        copy.fill = source.fill;
        copy.scaleX = source.scaleX;
        copy.scaleY = source.scaleY;
        copy.surface = source.surface;
        copy.stereo3d = source.stereo3d;
        copy.spherePreset = source.spherePreset;
        copy.sphereCenter = source.sphereCenter == null ? new Vector3f() : new Vector3f(source.sphereCenter);
        copy.sphereRadius = source.sphereRadius;
        copy.sphereLat = source.sphereLat;
        copy.sphereLon = source.sphereLon;
        copy.sphereRotX = source.sphereRotX;
        copy.sphereRotY = source.sphereRotY;
        copy.sphereRotZ = source.sphereRotZ;
        copy.sphereSkybox = source.sphereSkybox;
        copy.skipPercent = source.skipPercent;
        copy.idlePlayEntries = source.idlePlayEntries == null ? new ArrayList<>() : new ArrayList<>(source.idlePlayEntries);
        copy.idlePlayUrls = new ArrayList<>();
        copy.idlePlayRandom = source.idlePlayRandom;
        copy.metadata = copyMetadata(source.metadata);
        copy.playlist = copyPlaylist(source.playlist);
        copy.playbackResumeProgress = source.playbackResumeProgress;
        return copy;
    }

    private static ScreenMetadata copyMetadata(ScreenMetadata source) {
        ScreenMetadata copy = new ScreenMetadata();
        copy.values = new HashMap<>();
        if (source == null) return copy;
        for (var entry : source.entries().entrySet()) {
            copy.values.put(entry.getKey(), copyMetaValue(entry.getValue()));
        }
        return copy;
    }

    private static MetaValue copyMetaValue(MetaValue source) {
        MetaValue copy = new MetaValue();
        if (source == null) return copy;
        copy.type = source.type;
        copy.boolValue = source.boolValue;
        copy.intValue = source.intValue;
        copy.longValue = source.longValue;
        copy.floatValue = source.floatValue;
        copy.doubleValue = source.doubleValue;
        copy.stringValue = source.stringValue;
        copy.boolArray = source.boolArray == null ? null : source.boolArray.clone();
        copy.intArray = source.intArray == null ? null : source.intArray.clone();
        copy.floatArray = source.floatArray == null ? null : source.floatArray.clone();
        copy.stringArray = source.stringArray == null ? null : source.stringArray.clone();
        return copy;
    }

    private static ArrayList<VideoInfo> copyPlaylist(ArrayList<VideoInfo> source) {
        ArrayList<VideoInfo> copy = new ArrayList<>();
        if (source == null) return copy;
        for (VideoInfo info : source) {
            if (info == null) continue;
            copy.add(new VideoInfo(
                    info.playerName(),
                    info.name(),
                    info.path(),
                    info.rawPath(),
                    info.expire(),
                    info.seekable(),
                    info.params() == null ? null : info.params().clone(),
                    info.durationMs()
            ));
        }
        return copy;
    }
}
