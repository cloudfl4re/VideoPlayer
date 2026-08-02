package com.github.squi2rel.vp.creation;

import com.github.squi2rel.vp.video.AudioLevelSnapshot;
import com.github.squi2rel.vp.video.ClientVideoScreen;
import com.github.squi2rel.vp.video.IVideoPlayer;

import java.util.ArrayDeque;
import java.util.List;

final class DiagnosticsReviewSession {
    private static final int MAX_HISTORY_SAMPLES = 100;

    private final ArrayDeque<AudioLevelSnapshot> history = new ArrayDeque<>(MAX_HISTORY_SAMPLES);
    private ClientVideoScreen screen;
    private IVideoPlayer player;
    private boolean muted = true;
    private boolean handoff;
    private long lastSampleAt;

    void select(ClientVideoScreen nextScreen) {
        if (screen == nextScreen) {
            tick();
            return;
        }
        restore();
        screen = nextScreen;
        player = nextScreen == null ? null : nextScreen.player;
        clearHistory();
        apply();
    }

    void tick() {
        if (screen == null) return;
        if (player != screen.player) {
            player = screen.player;
            clearHistory();
            apply();
        }
        AudioLevelSnapshot snapshot = player == null ? AudioLevelSnapshot.waiting() : player.audioLevel();
        if (snapshot.status() != AudioLevelSnapshot.Status.AVAILABLE) return;
        if (snapshot.sampledAtMs() <= lastSampleAt) return;
        lastSampleAt = snapshot.sampledAtMs();
        while (history.size() >= MAX_HISTORY_SAMPLES) history.removeFirst();
        history.addLast(snapshot);
    }

    void toggleMute() {
        muted = !muted;
        apply();
    }

    boolean muted() {
        return muted;
    }

    AudioLevelSnapshot currentLevel() {
        return player == null ? AudioLevelSnapshot.waiting() : player.audioLevel();
    }

    List<AudioLevelSnapshot> history() {
        return List.copyOf(history);
    }

    void releaseScreen() {
        restore();
        screen = null;
        player = null;
        clearHistory();
    }

    void close() {
        releaseScreen();
        handoff = false;
    }

    void beginHandoff() {
        handoff = true;
    }

    boolean consumeHandoff() {
        if (!handoff) return false;
        handoff = false;
        return true;
    }

    private void apply() {
        if (player == null) return;
        if (muted) player.setOutputVolume(0);
        else player.clearOutputVolume();
    }

    private void restore() {
        if (player != null) player.clearOutputVolume();
    }

    private void clearHistory() {
        history.clear();
        lastSampleAt = 0L;
    }
}
