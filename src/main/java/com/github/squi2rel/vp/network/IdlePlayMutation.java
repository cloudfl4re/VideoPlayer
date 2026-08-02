package com.github.squi2rel.vp.network;

import com.github.squi2rel.vp.video.IdlePlayEntry;

import java.util.UUID;

public record IdlePlayMutation(IdlePlayAction action, String url, UUID entryId, int priority, boolean random) {
    public IdlePlayMutation {
        url = url == null ? "" : url;
        entryId = entryId == null ? IdlePlayEntry.UNKNOWN_UUID : entryId;
    }

    public static IdlePlayMutation add(String url, int priority) {
        return new IdlePlayMutation(IdlePlayAction.ADD, url, IdlePlayEntry.UNKNOWN_UUID, priority, false);
    }

    public static IdlePlayMutation remove(UUID entryId) {
        return new IdlePlayMutation(IdlePlayAction.REMOVE, "", entryId, 0, false);
    }

    public static IdlePlayMutation setPriority(UUID entryId, int priority) {
        return new IdlePlayMutation(IdlePlayAction.SET_PRIORITY, "", entryId, priority, false);
    }

    public static IdlePlayMutation adjustPriority(UUID entryId, int delta) {
        return new IdlePlayMutation(IdlePlayAction.ADJUST_PRIORITY, "", entryId, delta, false);
    }

    public int delta() {
        return priority;
    }

    public static IdlePlayMutation clear() {
        return new IdlePlayMutation(IdlePlayAction.CLEAR, "", IdlePlayEntry.UNKNOWN_UUID, 0, false);
    }

    public static IdlePlayMutation setMode(boolean random) {
        return new IdlePlayMutation(IdlePlayAction.SET_MODE, "", IdlePlayEntry.UNKNOWN_UUID, 0, random);
    }
}
