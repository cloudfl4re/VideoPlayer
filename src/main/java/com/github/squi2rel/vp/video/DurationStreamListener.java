package com.github.squi2rel.vp.video;

import com.github.squi2rel.vp.VideoPlayerMain;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class DurationStreamListener implements IVideoListener {
    private final long durationMs;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean finished = new AtomicBoolean(true);
    private volatile boolean playingState;
    private volatile long progressMs;
    private volatile long updateMs;
    private volatile ScheduledFuture<?> stopTask;
    private Consumer<Boolean> playing = seekable -> {};
    private Runnable stopped = () -> {};

    DurationStreamListener(long durationMs) {
        this(durationMs, VideoPlayerMain.scheduler);
    }

    DurationStreamListener(long durationMs, ScheduledExecutorService scheduler) {
        this.durationMs = Math.max(0L, durationMs);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public long getProgress() {
        if (!playingState) return -1;
        long progress = progressMs + Math.max(0, System.currentTimeMillis() - updateMs);
        return durationMs > 0 ? Math.min(progress, durationMs) : progress;
    }

    @Override
    public void setProgress(long progress) {
        progressMs = Math.max(0, progress);
        updateMs = System.currentTimeMillis();
        scheduleStop();
    }

    @Override
    public boolean isPlaying() {
        return playingState;
    }

    @Override
    public void playing(Consumer<Boolean> playing) {
        this.playing = playing == null ? seekable -> {} : playing;
    }

    @Override
    public void stopped(Runnable stopped) {
        this.stopped = stopped == null ? () -> {} : stopped;
    }

    @Override
    public void errored(Runnable errored) {
    }

    @Override
    public void timeout(Runnable timeout) {
    }

    @Override
    public void listen() {
        finished.set(false);
        progressMs = 0;
        updateMs = System.currentTimeMillis();
        playingState = true;
        playing.accept(true);
        scheduleStop();
    }

    @Override
    public void cancel() {
        if (!finished.compareAndSet(false, true)) return;
        playingState = false;
        ScheduledFuture<?> task = stopTask;
        if (task != null) task.cancel(false);
    }

    private void scheduleStop() {
        ScheduledFuture<?> existing = stopTask;
        if (existing != null) existing.cancel(false);
        if (!playingState || finished.get() || durationMs <= 0) return;
        long delay = Math.max(0L, durationMs - getProgress());
        stopTask = scheduler.schedule(this::completeStopped, delay, TimeUnit.MILLISECONDS);
    }

    private void completeStopped() {
        if (!finished.compareAndSet(false, true)) return;
        playingState = false;
        stopped.run();
    }
}
