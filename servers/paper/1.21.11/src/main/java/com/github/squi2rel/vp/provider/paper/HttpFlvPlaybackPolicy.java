package com.github.squi2rel.vp.provider.paper;

import com.github.squi2rel.vp.provider.VideoInfo;

import java.net.URI;
import java.util.Locale;

public final class HttpFlvPlaybackPolicy {
    private HttpFlvPlaybackPolicy() {
    }

    public static VideoInfo normalize(VideoInfo info) {
        if (info == null || !isHttpFlv(info.path())) return info;
        if (!info.seekable() && info.durationMs() == 0L) return info;
        return new VideoInfo(
                info.playerName(),
                info.name(),
                info.path(),
                info.rawPath(),
                info.expire(),
                false,
                info.params(),
                0L
        );
    }

    public static boolean isHttpFlv(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return false;
        try {
            URI uri = URI.create(rawPath.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) return false;
            String path = uri.getPath();
            return path != null && path.toLowerCase(Locale.ROOT).endsWith(".flv");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
