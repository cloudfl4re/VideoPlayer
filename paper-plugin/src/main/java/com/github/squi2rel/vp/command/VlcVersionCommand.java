package com.github.squi2rel.vp.command;

import com.github.squi2rel.vp.ClientVersionTracker;
import com.github.squi2rel.vp.i18n.PaperTexts;
import com.github.squi2rel.vp.i18n.VpTranslation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class VlcVersionCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(ClientVersionTracker.VERSION_PERMISSION)) {
            sender.sendMessage(PaperTexts.text(VpTranslation.of(
                    "error.videoplayer.permission_denied",
                    "Permission denied"
            )).color(NamedTextColor.RED));
            return true;
        }
        List<ClientVersionTracker.ClientVersion> versions = ClientVersionTracker.versionSnapshot();
        sender.sendMessage(PaperTexts.text(VpTranslation.of(
                "message.videoplayer.client_versions_header",
                "Connected players and VideoPlayer client versions"
        )).color(NamedTextColor.GOLD));
        if (versions.isEmpty()) {
            sender.sendMessage(PaperTexts.text(VpTranslation.of(
                    "message.videoplayer.client_versions_empty",
                    "No VideoPlayer client versions were detected"
            )).color(NamedTextColor.GRAY));
            return true;
        }
        for (ClientVersionTracker.ClientVersion version : versions) {
            Component line = Component.text(version.playerName() + ": ", NamedTextColor.GOLD)
                    .append(Component.text(
                            version.version(),
                            version.compatible() ? NamedTextColor.GREEN : NamedTextColor.RED
                    ));
            sender.sendMessage(line);
        }
        return true;
    }
}
