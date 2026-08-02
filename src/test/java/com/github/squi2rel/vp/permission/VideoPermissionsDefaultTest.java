package com.github.squi2rel.vp.permission;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VideoPermissionsDefaultTest {
    @AfterEach
    void resetPermissions() {
        VideoPermissions.reset();
    }

    @Test
    void nonOperatorsCannotSeekOrRemove() {
        VideoPermissionPlayer player = new VideoPermissionPlayer() {
            @Override
            public UUID uuid() {
                return UUID.fromString("00000000-0000-0000-0000-000000000001");
            }

            @Override
            public String name() {
                return "player";
            }

            @Override
            public boolean opOrGameMaster() {
                return false;
            }
        };

        for (VideoPermissionAction action : VideoPermissionAction.values()) {
            boolean expected = action != VideoPermissionAction.SEEK
                    && action != VideoPermissionAction.REMOVE_AREA
                    && action != VideoPermissionAction.REMOVE_SCREEN;
            assertEquals(expected, VideoPermissions.allowed(player, action, VideoPermissionContext.global("minecraft:overworld")));
        }
    }
}
