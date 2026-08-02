package com.github.squi2rel.vp.video;

import com.github.squi2rel.vp.network.ByteBufUtils;
import com.github.squi2rel.vp.provider.VideoUrlNormalizer;

import java.util.UUID;

public record IdlePlayEntry(UUID id, String url, UUID addedBy, String addedByName, int priority) {
    public static final int MIN_PRIORITY = 0;
    public static final int MAX_PRIORITY = 100;
    public static final int MAX_ADDED_BY_NAME_BYTES = 64;
    public static final UUID UNKNOWN_UUID = new UUID(0L, 0L);

    public IdlePlayEntry {
        id = id == null ? UNKNOWN_UUID : id;
        url = VideoUrlNormalizer.normalizeSubmittedUrl(url);
        addedBy = addedBy == null ? UNKNOWN_UUID : addedBy;
        addedByName = ByteBufUtils.truncateUtf8(addedByName == null ? "" : addedByName.trim(), MAX_ADDED_BY_NAME_BYTES);
        priority = Math.clamp(priority, MIN_PRIORITY, MAX_PRIORITY);
    }

    public static IdlePlayEntry create(String url, UUID addedBy, String addedByName, int priority) {
        return new IdlePlayEntry(UUID.randomUUID(), url, addedBy, addedByName, priority);
    }

    public static IdlePlayEntry legacy(String url) {
        return new IdlePlayEntry(UUID.randomUUID(), url, UNKNOWN_UUID, "", MIN_PRIORITY);
    }

    public boolean legacyOwner() {
        return UNKNOWN_UUID.equals(addedBy) && addedByName.isEmpty();
    }

    public IdlePlayEntry withPriority(int nextPriority) {
        return new IdlePlayEntry(id, url, addedBy, addedByName, nextPriority);
    }
}
