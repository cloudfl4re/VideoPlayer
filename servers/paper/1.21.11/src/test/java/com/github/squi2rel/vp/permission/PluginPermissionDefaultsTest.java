package com.github.squi2rel.vp.permission;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginPermissionDefaultsTest {
    @Test
    void permissionDefaultsMatchPublicAndRestrictedActions() throws IOException {
        Map<String, String> defaults = readDefaults(Path.of("src/main/resources/plugin.yml"));

        assertEquals("op", defaults.get("videoplayer.admin"));
        assertEquals("op", defaults.get("videoplayer.version"));
        assertEquals("op", defaults.get("videoplayer.joinmessage"));
        for (VideoPermissionAction action : VideoPermissionAction.values()) {
            String expected = switch (action) {
                case SEEK, REMOVE_AREA, REMOVE_SCREEN -> "op";
                default -> "true";
            };
            assertEquals(expected, defaults.get("videoplayer.action." + action.name().toLowerCase(java.util.Locale.ROOT)));
        }
        assertEquals(VideoPermissionAction.values().length + 3, defaults.size());

        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"))
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        assertTrue(plugin.contains("  vlc:\n    description: Manage VideoPlayer server notifications.\n    usage: /videoplayer:vlc joinmessage\n    permission: videoplayer.joinmessage"));
        assertTrue(plugin.contains("  vlcversion:\n    description: Show connected VideoPlayer client versions.\n    usage: /vlcversion\n    permission: videoplayer.version"));
    }

    private static Map<String, String> readDefaults(Path path) throws IOException {
        HashMap<String, String> defaults = new HashMap<>();
        String permission = null;
        for (String line : Files.readAllLines(path)) {
            if (line.startsWith("  videoplayer.") && line.endsWith(":")) {
                permission = line.trim().substring(0, line.trim().length() - 1);
            } else if (permission != null && line.startsWith("    default: ")) {
                defaults.put(permission, line.substring("    default: ".length()).trim());
            }
        }
        return defaults;
    }
}
