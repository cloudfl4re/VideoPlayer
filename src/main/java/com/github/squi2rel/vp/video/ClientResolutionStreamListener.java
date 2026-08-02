package com.github.squi2rel.vp.video;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.Objects;

final class ClientResolutionStreamListener implements IVideoListener, ClientPlaybackResolutionListener {
    private final AtomicBoolean finished = new AtomicBoolean(true);
    private volatile boolean playingState;
    private volatile boolean live;
    private volatile long startedAtMs;
    private volatile DurationStreamListener durationListener;
    private final LongFunction<DurationStreamListener> durationListenerFactory;
    private Consumer<Boolean> playing = seekable -> {};
    private Runnable stopped = () -> {};
    private Runnable errored = () -> {};
    private Runnable timeout = () -> {};

    ClientResolutionStreamListener() {
        this(DurationStreamListener::new);
    }

    ClientResolutionStreamListener(ScheduledExecutorService scheduler) {
        this(durationMs -> new DurationStreamListener(durationMs, scheduler));
    }

    private ClientResolutionStreamListener(LongFunction<DurationStreamListener> durationListenerFactory) {
        this.durationListenerFactory = Objects.requireNonNull(durationListenerFactory, "durationListenerFactory");
    }

    @Override
    public long getProgress() {
        DurationStreamListener listener = durationListener;
        if (listener != null) return listener.getProgress();
        return playingState ? 0L : -1L;
    }

    @Override
    public void setProgress(long progress) {
        DurationStreamListener listener = durationListener;
        if (listener != null) listener.setProgress(progress);
    }

    @Override
    public boolean isPlaying() {
        DurationStreamListener listener = durationListener;
        return playingState && (listener == null || listener.isPlaying());
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
        this.errored = errored == null ? () -> {} : errored;
    }

    @Override
    public void timeout(Runnable timeout) {
        this.timeout = timeout == null ? () -> {} : timeout;
    }

    @Override
    public void listen() {
        DurationStreamListener listener;
        synchronized (this) {
            if (!finished.compareAndSet(true, false)) return;
            playingState = true;
            startedAtMs = System.currentTimeMillis();
            listener = durationListener;
        }
        if (listener == null) {
            playing.accept(true);
        } else {
            listener.listen();
        }
    }

    @Override
    public void cancel() {
        DurationStreamListener listener;
        synchronized (this) {
            if (!finished.compareAndSet(false, true)) return;
            playingState = false;
            listener = durationListener;
        }
        if (listener != null) listener.cancel();
    }

    @Override
    public boolean resolveFinite(long durationMs) {
        if (durationMs <= 0) return false;
        DurationStreamListener listener;
        boolean listening;
        synchronized (this) {
            if (finished.get() || live || durationListener != null) return false;
            listener = durationListenerFactory.apply(durationMs);
            if (listener == null) return false;
            configure(listener);
            durationListener = listener;
            listening = playingState;
        }
        if (listening) {
            listener.listen();
            listener.setProgress(Math.max(0L, System.currentTimeMillis() - startedAtMs));
        }
        return true;
    }

    @Override
    public boolean resolveLive() {
        synchronized (this) {
            if (finished.get() || live || durationListener != null) return false;
            live = true;
            return true;
        }
    }

    private void configure(DurationStreamListener listener) {
        listener.playing(seekable -> playing.accept(seekable));
        listener.stopped(() -> {
            if (!finished.compareAndSet(false, true)) return;
            playingState = false;
            stopped.run();
        });
        listener.errored(errored);
        listener.timeout(timeout);
    }
}
