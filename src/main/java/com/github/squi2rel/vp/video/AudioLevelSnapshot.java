package com.github.squi2rel.vp.video;

public record AudioLevelSnapshot(Status status, float rmsDb, float peakDb, long sampledAtMs) {
    public static final float MIN_DB = -60f;
    public static final float MAX_DB = 0f;

    public AudioLevelSnapshot {
        status = status == null ? Status.WAITING : status;
        rmsDb = clampDb(rmsDb);
        peakDb = clampDb(peakDb);
        sampledAtMs = Math.max(0L, sampledAtMs);
    }

    public static AudioLevelSnapshot available(float rmsDb, float peakDb, long sampledAtMs) {
        return new AudioLevelSnapshot(Status.AVAILABLE, rmsDb, peakDb, sampledAtMs);
    }

    public static AudioLevelSnapshot waiting() {
        return new AudioLevelSnapshot(Status.WAITING, MIN_DB, MIN_DB, 0L);
    }

    public static AudioLevelSnapshot noAudio() {
        return new AudioLevelSnapshot(Status.NO_AUDIO, MIN_DB, MIN_DB, 0L);
    }

    public static AudioLevelSnapshot unsupported() {
        return new AudioLevelSnapshot(Status.UNSUPPORTED, MIN_DB, MIN_DB, 0L);
    }

    private static float clampDb(float value) {
        if (!Float.isFinite(value)) return MIN_DB;
        return Math.clamp(value, MIN_DB, MAX_DB);
    }

    public enum Status {
        AVAILABLE,
        WAITING,
        NO_AUDIO,
        UNSUPPORTED
    }
}
