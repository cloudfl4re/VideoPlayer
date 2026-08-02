package com.github.squi2rel.vp.network;

public enum ClientPlaybackResolution {
    FINITE(0),
    LIVE(1),
    FAILED(2);

    public final int id;

    ClientPlaybackResolution(int id) {
        this.id = id;
    }

    public static ClientPlaybackResolution fromId(int id) {
        for (ClientPlaybackResolution value : values()) {
            if (value.id == id) return value;
        }
        return null;
    }
}
