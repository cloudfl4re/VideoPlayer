package com.github.squi2rel.vp.command;

import com.github.squi2rel.vp.ClientVersionTracker;
import com.github.squi2rel.vp.i18n.PaperTexts;
import com.github.squi2rel.vp.i18n.VpTranslation;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class VlcCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("joinmessage")) {
            sender.sendMessage(PaperTexts.text(VpTranslation.of(
                    "message.videoplayer.joinmessage_usage",
                    "Usage: /videoplayer:vlc joinmessage"
            )).color(NamedTextColor.AQUA));
            return true;
        }
        if (!sender.hasPermission(ClientVersionTracker.JOIN_MESSAGE_PERMISSION)) {
            sender.sendMessage(PaperTexts.text(VpTranslation.of(
                    "error.videoplayer.permission_denied",
                    "Permission denied"
            )).color(NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PaperTexts.text(VpTranslation.of(
                    "error.videoplayer.player_only",
                    "Only players can use this command"
            )).color(NamedTextColor.RED));
            return true;
        }
        boolean enabled = ClientVersionTracker.toggleSubscription(player);
        sender.sendMessage(PaperTexts.text(VpTranslation.of(
                enabled ? "message.videoplayer.joinmessage_enabled" : "message.videoplayer.joinmessage_disabled",
                enabled ? "VideoPlayer join-version notifications enabled" : "VideoPlayer join-version notifications disabled"
        )).color(enabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission(ClientVersionTracker.JOIN_MESSAGE_PERMISSION)) return List.of();
        if (args.length == 1 && "joinmessage".startsWith(args[0].toLowerCase())) return List.of("joinmessage");
        return List.of();
    }
}
