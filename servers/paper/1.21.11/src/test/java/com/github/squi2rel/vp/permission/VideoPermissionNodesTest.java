package com.github.squi2rel.vp.permission;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoPermissionNodesTest {
    @Test
    void mapsEveryActionToCurrentAndLegacyNodes() {
        Map<VideoPermissionAction, String> legacy = Map.ofEntries(
                Map.entry(VideoPermissionAction.PLAY, "videoplayer.screen.request"),
                Map.entry(VideoPermissionAction.SEEK, "videoplayer.screen.sync"),
                Map.entry(VideoPermissionAction.SYNC, "videoplayer.screen.sync"),
                Map.entry(VideoPermissionAction.VOTE_SKIP, "videoplayer.screen.skip"),
                Map.entry(VideoPermissionAction.FORCE_SKIP, "videoplayer.screen.forceskip"),
                Map.entry(VideoPermissionAction.SET_SKIP_PERCENT, "videoplayer.screen.skippercent"),
                Map.entry(VideoPermissionAction.CREATE_AREA, "videoplayer.area.create"),
                Map.entry(VideoPermissionAction.REMOVE_AREA, "videoplayer.area.remove"),
                Map.entry(VideoPermissionAction.CREATE_SCREEN, "videoplayer.screen.create"),
                Map.entry(VideoPermissionAction.REMOVE_SCREEN, "videoplayer.screen.remove"),
                Map.entry(VideoPermissionAction.UPDATE_SCREEN, "videoplayer.screen.create"),
                Map.entry(VideoPermissionAction.SET_UV, "videoplayer.screen.slice"),
                Map.entry(VideoPermissionAction.SET_SCALE, "videoplayer.screen.setscale"),
                Map.entry(VideoPermissionAction.SET_METADATA, "videoplayer.screen.setmeta"),
                Map.entry(VideoPermissionAction.SET_IDLE_PLAY, "videoplayer.screen.idleplay"),
                Map.entry(VideoPermissionAction.AUTO_SYNC, "videoplayer.screen.sync"),
                Map.entry(VideoPermissionAction.OPEN_MENU, "videoplayer.screen.openmenu")
        );

        for (VideoPermissionAction action : VideoPermissionAction.values()) {
            assertEquals("videoplayer.action." + action.name().toLowerCase(java.util.Locale.ROOT), VideoPermissionNodes.current(action));
            assertEquals(legacy.get(action), VideoPermissionNodes.legacy(action).getFirst());
            assertTrue(VideoPermissionNodes.residenceFlags().contains(VideoPermissionNodes.current(action)));
            assertTrue(VideoPermissionNodes.residenceFlags().contains(legacy.get(action)));
        }
    }
}
