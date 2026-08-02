package com.github.squi2rel.vp;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class VideoConnectionDiagnostics {
    public enum State {
        IDLE,
        CONNECTING,
        CONNECTED,
        CHANNEL_UNAVAILABLE,
        VERSION_MISMATCH,
        TIMED_OUT,
        DISCONNECTED
    }

    public enum Trigger {
        JOIN,
        MANUAL_RETRY
    }

    public enum EventType {
        ATTEMPT_STARTED,
        CHANNEL_UNAVAILABLE,
        CHANNEL_AVAILABLE,
        TIMED_OUT,
        CONNECTED,
        VERSION_MISMATCH,
        RETRY_BLOCKED,
        DISCONNECTED
    }

    public record Snapshot(
            State state,
            Trigger trigger,
            String address,
            String localVersion,
            String remoteVersion,
            int attempts,
            long elapsedMillis
    ) {
    }

    public record Event(EventType type, Snapshot snapshot) {
    }

    private final long timeoutMillis;
    private final LongSupplier clock;
    private final Consumer<Event> eventSink;
    private State state = State.IDLE;
    private Trigger trigger = Trigger.JOIN;
    private String address = "";
    private String localVersion = "";
    private String remoteVersion = "";
    private int attempts;
    private long startedAt = -1L;

    VideoConnectionDiagnostics(long timeoutMillis, LongSupplier clock, Consumer<Event> eventSink) {
        if (timeoutMillis < 1L) throw new IllegalArgumentException("timeoutMillis must be positive");
        this.timeoutMillis = timeoutMillis;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.eventSink = Objects.requireNonNull(eventSink, "eventSink");
    }

    public void beginJoin(String address, String localVersion) {
        begin(Trigger.JOIN, address, localVersion);
    }

    public boolean beginManualRetry(String address, String localVersion) {
        begin(Trigger.MANUAL_RETRY, address, localVersion);
        return true;
    }

    public void channelUnavailable() {
        if (!active() || state == State.VERSION_MISMATCH || state == State.CHANNEL_UNAVAILABLE) return;
        state = State.CHANNEL_UNAVAILABLE;
        emit(EventType.CHANNEL_UNAVAILABLE);
    }

    public void channelAvailable() {
        if (state != State.CHANNEL_UNAVAILABLE) return;
        state = State.CONNECTING;
        startedAt = clock.getAsLong();
        emit(EventType.CHANNEL_AVAILABLE);
    }

    public void handshakeSent() {
        if (state == State.CONNECTING || state == State.TIMED_OUT) attempts++;
    }

    public void handshakeResponse(String remoteVersion) {
        if (!active() || state == State.VERSION_MISMATCH) return;
        this.remoteVersion = normalized(remoteVersion);
    }

    public void connected(String remoteVersion) {
        if (!active()) return;
        this.remoteVersion = normalized(remoteVersion);
        if (state == State.CONNECTED) return;
        state = State.CONNECTED;
        emit(EventType.CONNECTED);
    }

    public void versionMismatch(String remoteVersion) {
        if (!active()) return;
        String normalizedRemote = normalized(remoteVersion);
        if (state == State.VERSION_MISMATCH && this.remoteVersion.equals(normalizedRemote)) return;
        this.remoteVersion = normalizedRemote;
        state = State.VERSION_MISMATCH;
        emit(EventType.VERSION_MISMATCH);
    }

    public void tick() {
        if (state != State.CONNECTING || attempts == 0) return;
        if (elapsedMillis() < timeoutMillis) return;
        state = State.TIMED_OUT;
        emit(EventType.TIMED_OUT);
    }

    public void disconnected() {
        if (!active()) return;
        state = State.DISCONNECTED;
        emit(EventType.DISCONNECTED);
        address = "";
        localVersion = "";
        remoteVersion = "";
        attempts = 0;
        startedAt = -1L;
    }

    public Snapshot snapshot() {
        return new Snapshot(state, trigger, address, localVersion, remoteVersion, attempts, elapsedMillis());
    }

    private void begin(Trigger trigger, String address, String localVersion) {
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.address = normalized(address);
        this.localVersion = normalized(localVersion);
        remoteVersion = "";
        attempts = 0;
        startedAt = clock.getAsLong();
        state = State.CONNECTING;
        emit(EventType.ATTEMPT_STARTED);
    }

    private boolean active() {
        return state != State.IDLE && state != State.DISCONNECTED;
    }

    private long elapsedMillis() {
        if (startedAt < 0L) return 0L;
        return Math.max(0L, clock.getAsLong() - startedAt);
    }

    private void emit(EventType type) {
        eventSink.accept(new Event(type, snapshot()));
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
