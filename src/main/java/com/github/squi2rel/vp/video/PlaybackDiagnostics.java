package com.github.squi2rel.vp.video;

import com.github.squi2rel.vp.network.ByteBufUtils;

public record PlaybackDiagnostics(
        String currentTitle,
        String queuedTitle,
        int queueSize,
        long generation,
        long progressMs,
        boolean playing,
        boolean resolving,
        boolean idle,
        boolean seekable,
        int retryAttempt,
        long nextRetryAtMs,
        PlaybackFailureReason failureReason,
        String failureMessage,
        long failureAtMs,
        boolean awaitingClientResolution,
        boolean reporterAssigned,
        String backendState
) {
    public static final int MAX_TEXT_BYTES = 256;
    public static final int MAX_BACKEND_STATE_BYTES = 64;

    public PlaybackDiagnostics {
        currentTitle = ByteBufUtils.truncateUtf8(currentTitle, MAX_TEXT_BYTES);
        queuedTitle = ByteBufUtils.truncateUtf8(queuedTitle, MAX_TEXT_BYTES);
        queueSize = Math.max(0, Math.min(PlaybackQueue.MAX_ITEMS, queueSize));
        generation = Math.max(0L, generation);
        progressMs = Math.max(-1L, progressMs);
        retryAttempt = Math.max(0, retryAttempt);
        nextRetryAtMs = Math.max(0L, nextRetryAtMs);
        failureReason = failureReason == null ? PlaybackFailureReason.NONE : failureReason;
        failureMessage = ByteBufUtils.truncateUtf8(failureMessage, MAX_TEXT_BYTES);
        failureAtMs = Math.max(0L, failureAtMs);
        backendState = ByteBufUtils.truncateUtf8(backendState, MAX_BACKEND_STATE_BYTES);
    }

    public static PlaybackDiagnostics empty(String backendState) {
        return new PlaybackDiagnostics("", "", 0, 0L, -1L, false, false, false, false,
                0, 0L, PlaybackFailureReason.NONE, "", 0L, false, false, backendState);
    }
}
