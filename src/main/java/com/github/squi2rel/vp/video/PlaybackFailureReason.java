package com.github.squi2rel.vp.video;

import java.util.HashMap;
import java.util.Map;

public enum PlaybackFailureReason {
    NONE(0, false, ""),
    RESOLUTION(1, true, "Unable to resolve the media source"),
    SOURCE_REJECTED(2, false, "The resolved media source is not allowed"),
    LISTENER_START(3, true, "Unable to start the playback backend"),
    PLAYBACK_ERROR(4, true, "The playback backend reported an error"),
    PLAYBACK_TIMEOUT(5, true, "The playback backend timed out while loading media"),
    CLIENT_RESOLUTION(6, false, "A client could not resolve playback metadata");

    private static final Map<Integer, PlaybackFailureReason> BY_ID = new HashMap<>();

    static {
        for (PlaybackFailureReason reason : values()) {
            BY_ID.put(reason.id, reason);
        }
    }

    private final int id;
    private final boolean retryable;
    private final String fallback;

    PlaybackFailureReason(int id, boolean retryable, String fallback) {
        this.id = id;
        this.retryable = retryable;
        this.fallback = fallback;
    }

    public int id() {
        return id;
    }

    public boolean retryable() {
        return retryable;
    }

    public String fallback() {
        return fallback;
    }

    public static PlaybackFailureReason fromId(int id) {
        return BY_ID.getOrDefault(id, NONE);
    }
}
