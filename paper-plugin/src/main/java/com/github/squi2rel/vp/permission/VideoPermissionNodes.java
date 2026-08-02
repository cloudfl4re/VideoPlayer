package com.github.squi2rel.vp.permission;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

final class VideoPermissionNodes {
    private VideoPermissionNodes() {
    }

    static String current(VideoPermissionAction action) {
        return "videoplayer.action." + action.name().toLowerCase(Locale.ROOT);
    }

    static List<String> legacy(VideoPermissionAction action) {
        return switch (action) {
            case PLAY -> List.of("videoplayer.screen.request");
            case SEEK, SYNC, AUTO_SYNC -> List.of("videoplayer.screen.sync");
            case VOTE_SKIP -> List.of("videoplayer.screen.skip");
            case FORCE_SKIP -> List.of("videoplayer.screen.forceskip");
            case SET_SKIP_PERCENT -> List.of("videoplayer.screen.skippercent");
            case CREATE_AREA -> List.of("videoplayer.area.create");
            case REMOVE_AREA -> List.of("videoplayer.area.remove");
            case CREATE_SCREEN, UPDATE_SCREEN -> List.of("videoplayer.screen.create");
            case REMOVE_SCREEN -> List.of("videoplayer.screen.remove");
            case SET_UV -> List.of("videoplayer.screen.slice");
            case SET_SCALE -> List.of("videoplayer.screen.setscale");
            case SET_METADATA -> List.of("videoplayer.screen.setmeta");
            case SET_IDLE_PLAY -> List.of("videoplayer.screen.idleplay");
            case OPEN_MENU -> List.of("videoplayer.screen.openmenu");
        };
    }

    static List<String> residenceFlags() {
        LinkedHashSet<String> flags = new LinkedHashSet<>();
        for (VideoPermissionAction action : VideoPermissionAction.values()) {
            flags.add(current(action));
            flags.addAll(legacy(action));
        }
        return List.copyOf(new ArrayList<>(flags));
    }
}
