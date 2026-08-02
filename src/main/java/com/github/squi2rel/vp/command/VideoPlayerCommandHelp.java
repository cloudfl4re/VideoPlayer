package com.github.squi2rel.vp.command;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class VideoPlayerCommandHelp {
    private static final List<Entry> ENTRIES = List.of(
            entry("play", "<url>", "Play a URL on the current screen.", "Requires a server connection and a current screen. The URL must be supported by the configured provider."),
            entry("playthat", "<area> <screen> <url>", "Play a URL on a named screen.", "The area and screen must be loaded, and the target screen must be an interactable screen."),
            entry("skip", "[force] | <area> <screen> <force>", "Vote to skip the current or named screen's video.", "The current screen or the named screen must exist. force=true bypasses the normal vote when permitted."),
            entry("skipPercent", "<percent: 0..1.01>", "Set the skip-vote threshold.", "The value is a ratio from 0 to 1.01 and is sent to the server."),
            entry("volume", "<volume: 0..100>", "Set the local playback volume.", "The value is a percentage. It is persisted in the client configuration."),
            entry("brightness", "<brightness: 0..100>", "Set the local screen brightness.", "The value is a percentage and is persisted in the client configuration."),
            entry("backend", "[vlc|mpv]", "Select the playback backend or show the current backend.", "Only newly started videos use the selected backend. MPV requires its native runtime."),
            entry("audio", "[stereo|auto]", "Select the audio channel mode or show its configured and active values.", "stereo downmixes multichannel audio for two-channel output. auto lets the playback backend and system choose the output layout. Changes are saved locally and require a Minecraft restart to take effect."),
            entry("boot", "", "Open the VideoPlayer setup guide.", "Use this to install or repair native VLC/MPV runtimes and configure network options."),
            entry("diagnostics", "", "Open the playback diagnostics screen.", "The diagnostics screen opens muted for review and requires screen menu permission."),
            entry("biliAuth", "login|set <cookie>|clear|status", "Manage the local Bilibili login cookie.", "login opens QR login. set expects one browser Cookie header such as SESSDATA=value; bili_jct=value; buvid3=value; buvid4=value. Do not paste a Netscape cookie file or include a Cookie: prefix. SESSDATA and bili_jct are the important account fields; buvid3 and buvid4 are optional."),
            entry("youtubeAuth", "login|clear|status", "Manage local YouTube authentication.", "login opens the YouTube authentication screen. Configure either a Netscape cookies.txt file exported from your browser or a yt-dlp browser profile such as chrome:Default. A raw Cookie header is not accepted. When both are set, cookies.txt takes priority. The setting is local and used by yt-dlp for age-restricted videos, account access, and live streams. youtube-auth is an alias."),
            entry("danmaku", "", "Toggle Bilibili danmaku rendering.", "Requires a server connection. The setting applies to the local client."),
            entry("createArea", "<x1> <y1> <z1> <x2> <y2> <z2> <name>", "Create a video area from two corners.", "Requires a server connection and the server permission to create areas. Coordinates are world coordinates."),
            entry("removeArea", "<name>", "Remove a video area.", "The named area must be loaded and the server must grant the remove permission."),
            entry("createScreen", "<area> <name> <x1> <y1> <z1> ... <x4> <y4> <z4> <source>", "Create a screen with four vertices.", "The area must be loaded and the server must grant screen-create permission. Use \"\" as source for an independent screen; otherwise source is another real screen name."),
            entry("removeScreen", "<area> <name>", "Remove a screen from an area.", "The area and screen must be loaded and the server must grant screen-remove permission."),
            entry("list", "", "List the playback queue of the current screen.", "Requires a current screen inside a loaded video area."),
            entry("sync", "", "Request playback synchronization for the current screen.", "Requires a current screen and a server connection."),
            entry("slice", "<u1> <v1> <u2> <v2>", "Set the texture slice on the screen being looked at.", "Requires looking at an interactable screen. UV values are floating-point coordinates."),
            entry("stop", "", "Stop playback on the current screen locally.", "Requires a current screen inside a loaded video area."),
            entry("setmeta", "<area> <screen> ...", "Set, read, or remove screen metadata.", "The area and screen must be loaded. Built-in keys and custom metadata are subject to server validation and permissions."),
            entry("scale", "stretch|auto|set <scaleX: 0.0625..16> <scaleY: 0.0625..16>", "Change the scale of the screen being looked at.", "Requires looking at an interactable screen. set accepts values from 1/16 to 16."),
            entry("help", "[subcommand]", "Show command help.", "Use /videoplayer help <subcommand> for usage, ranges, and prerequisites. /vlc remains a compatible alias.")
    );

    private VideoPlayerCommandHelp() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Optional<Entry> find(String name) {
        if (name == null) return Optional.empty();
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        String lookup = normalized.equals("youtube-auth") ? "youtubeauth" : normalized;
        return ENTRIES.stream().filter(entry -> entry.name().toLowerCase(Locale.ROOT).equals(lookup)).findFirst();
    }

    private static Entry entry(String name, String usage, String summary, String details) {
        return new Entry(name, usage, summary, details);
    }

    public record Entry(String name, String usage, String summary, String details) {
        public Entry {
            name = name == null ? "" : name;
            usage = usage == null ? "" : usage;
            summary = summary == null ? "" : summary;
            details = details == null ? "" : details;
        }
    }
}
