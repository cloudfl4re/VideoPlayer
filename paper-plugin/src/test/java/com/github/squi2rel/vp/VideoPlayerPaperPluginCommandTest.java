package com.github.squi2rel.vp;

import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class VideoPlayerPaperPluginCommandTest {
    @Test
    void restoresMappingsRemovedByAPluginReload() {
        Plugin plugin = plugin("VideoPlayer");
        Command vlc = mock(Command.class);
        Command version = mock(Command.class);
        Command staleVlc = pluginCommand("VideoPlayer");
        Command staleVersion = pluginCommand("VideoPlayer");
        Command other = mock(Command.class);
        Map<String, Command> commands = new HashMap<>();
        commands.put("vlc", staleVlc);
        commands.put("videoplayer:vlc", staleVlc);
        commands.put("vlcversion", staleVersion);
        commands.put("videoplayer:vlcversion", staleVersion);
        commands.put("other:vlc", other);

        assertTrue(VideoPlayerPaperPlugin.restoreCommandMappings(commands, plugin, vlc, version));

        assertFalse(commands.containsKey("vlc"));
        assertSame(vlc, commands.get("videoplayer:vlc"));
        assertSame(version, commands.get("vlcversion"));
        assertSame(version, commands.get("videoplayer:vlcversion"));
        assertSame(other, commands.get("other:vlc"));
    }

    @Test
    void preservesAnotherPluginsBareVersionCommand() {
        Plugin plugin = plugin("VideoPlayer");
        Command vlc = mock(Command.class);
        Command version = mock(Command.class);
        Command otherVersion = pluginCommand("OtherPlugin");
        Map<String, Command> commands = new HashMap<>();
        commands.put("vlcversion", otherVersion);

        assertTrue(VideoPlayerPaperPlugin.restoreCommandMappings(commands, plugin, vlc, version));

        assertSame(otherVersion, commands.get("vlcversion"));
        assertSame(version, commands.get("videoplayer:vlcversion"));
    }

    private static Plugin plugin(String name) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn(name);
        return plugin;
    }

    private static Command pluginCommand(String pluginName) {
        Command command = mock(Command.class, withSettings().extraInterfaces(PluginIdentifiableCommand.class));
        Plugin plugin = plugin(pluginName);
        when(((PluginIdentifiableCommand) command).getPlugin()).thenReturn(plugin);
        return command;
    }
}
