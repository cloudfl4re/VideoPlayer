package com.github.squi2rel.vp.provider.paper;

import com.github.squi2rel.vp.provider.MediaAddressPolicy;
import com.github.squi2rel.vp.provider.VideoInfo;

public final class HttpFlvPlaybackPolicy {
    private HttpFlvPlaybackPolicy() {
    }

    public static VideoInfo normalize(VideoInfo info) {
        if (info == null || (!isHttpFlv(info.path()) && !isSrt(info.path()))) return info;
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
        return MediaAddressPolicy.isHttpFlv(rawPath);
    }

    public static boolean isSrt(String rawPath) {
        return MediaAddressPolicy.isSrt(rawPath);
    }
}
