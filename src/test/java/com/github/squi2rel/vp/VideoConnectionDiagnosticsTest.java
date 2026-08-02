package com.github.squi2rel.vp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoConnectionDiagnosticsTest {
    @Test
    void timesOutOnceWithoutCreatingAnotherAutomaticAttempt() {
        AtomicLong clock = new AtomicLong(1_000L);
        ArrayList<VideoConnectionDiagnostics.Event> events = new ArrayList<>();
        VideoConnectionDiagnostics diagnostics = new VideoConnectionDiagnostics(10_000L, clock::get, events::add);

        diagnostics.beginJoin("example.com:25565", "2.0.1");
        diagnostics.handshakeSent();
        clock.set(10_999L);
        diagnostics.tick();

        assertEquals(VideoConnectionDiagnostics.State.CONNECTING, diagnostics.snapshot().state());
        assertEquals(1, diagnostics.snapshot().attempts());
        assertEquals(1, events.size());

        clock.set(11_000L);
        diagnostics.tick();
        diagnostics.tick();

        assertEquals(VideoConnectionDiagnostics.State.TIMED_OUT, diagnostics.snapshot().state());
        assertEquals(1, diagnostics.snapshot().attempts());
        assertEquals(2, events.size());
        assertEquals(VideoConnectionDiagnostics.EventType.TIMED_OUT, events.get(1).type());

        clock.set(12_000L);
        diagnostics.connected("2.0.1");
        diagnostics.connected("2.0.1");

        assertEquals(VideoConnectionDiagnostics.State.CONNECTED, diagnostics.snapshot().state());
        assertEquals("2.0.1", diagnostics.snapshot().remoteVersion());
        assertEquals(11_000L, diagnostics.snapshot().elapsedMillis());
        assertEquals(3, events.size());
        assertEquals(VideoConnectionDiagnostics.EventType.CONNECTED, events.get(2).type());
    }

    @Test
    void unavailableChannelLogsOnlyStateChangesAndRestartsTimeoutWhenRecovered() {
        AtomicLong clock = new AtomicLong();
        ArrayList<VideoConnectionDiagnostics.Event> events = new ArrayList<>();
        VideoConnectionDiagnostics diagnostics = new VideoConnectionDiagnostics(10_000L, clock::get, events::add);

        diagnostics.beginJoin("play.example.net", "2.0.1");
        diagnostics.channelUnavailable();
        diagnostics.channelUnavailable();
        clock.set(30_000L);
        diagnostics.tick();

        assertEquals(VideoConnectionDiagnostics.State.CHANNEL_UNAVAILABLE, diagnostics.snapshot().state());
        assertEquals(2, events.size());

        diagnostics.channelAvailable();
        diagnostics.handshakeSent();
        clock.set(39_999L);
        diagnostics.tick();

        assertEquals(VideoConnectionDiagnostics.State.CONNECTING, diagnostics.snapshot().state());
        assertEquals(3, events.size());

        clock.set(40_000L);
        diagnostics.tick();

        assertEquals(VideoConnectionDiagnostics.State.TIMED_OUT, diagnostics.snapshot().state());
        assertEquals(4, events.size());
        assertEquals(VideoConnectionDiagnostics.EventType.CHANNEL_AVAILABLE, events.get(2).type());
        assertEquals(VideoConnectionDiagnostics.EventType.TIMED_OUT, events.get(3).type());
    }

    @Test
    void versionMismatchAllowsManualRetryAfterTheServerChanges() {
        AtomicLong clock = new AtomicLong(500L);
        ArrayList<VideoConnectionDiagnostics.Event> events = new ArrayList<>();
        VideoConnectionDiagnostics diagnostics = new VideoConnectionDiagnostics(10_000L, clock::get, events::add);

        diagnostics.beginJoin("server.example.org:25565", "2.0.1");
        diagnostics.handshakeSent();
        diagnostics.versionMismatch("2.0.0");
        clock.set(1_000L);

        assertTrue(diagnostics.beginManualRetry("server.example.org:25565", "2.0.1"));
        VideoConnectionDiagnostics.Snapshot snapshot = diagnostics.snapshot();

        assertEquals(VideoConnectionDiagnostics.State.CONNECTING, snapshot.state());
        assertEquals(VideoConnectionDiagnostics.Trigger.MANUAL_RETRY, snapshot.trigger());
        assertEquals("2.0.1", snapshot.localVersion());
        assertEquals("", snapshot.remoteVersion());
        assertEquals(0, snapshot.attempts());
        assertEquals(VideoConnectionDiagnostics.EventType.ATTEMPT_STARTED, events.get(events.size() - 1).type());
    }

    @Test
    void manualRetryStartsFreshAttemptWithoutDiscardingAddress() {
        AtomicLong clock = new AtomicLong(100L);
        ArrayList<VideoConnectionDiagnostics.Event> events = new ArrayList<>();
        VideoConnectionDiagnostics diagnostics = new VideoConnectionDiagnostics(10_000L, clock::get, events::add);

        diagnostics.beginJoin("localhost:25565", "2.0.1");
        diagnostics.handshakeSent();
        diagnostics.connected("2.0.1");
        clock.set(200L);

        assertTrue(diagnostics.beginManualRetry("localhost:25565", "2.0.1"));
        VideoConnectionDiagnostics.Snapshot snapshot = diagnostics.snapshot();

        assertEquals(VideoConnectionDiagnostics.State.CONNECTING, snapshot.state());
        assertEquals(VideoConnectionDiagnostics.Trigger.MANUAL_RETRY, snapshot.trigger());
        assertEquals("localhost:25565", snapshot.address());
        assertEquals("", snapshot.remoteVersion());
        assertEquals(0, snapshot.attempts());
        assertEquals(VideoConnectionDiagnostics.EventType.ATTEMPT_STARTED, events.get(events.size() - 1).type());
    }

    @Test
    void disconnectEventRetainsDetailsBeforeLiveStateIsCleared() {
        AtomicLong clock = new AtomicLong(1_000L);
        ArrayList<VideoConnectionDiagnostics.Event> events = new ArrayList<>();
        VideoConnectionDiagnostics diagnostics = new VideoConnectionDiagnostics(10_000L, clock::get, events::add);

        diagnostics.beginJoin("example.com:25565", "2.0.1");
        diagnostics.handshakeSent();
        clock.set(1_500L);
        diagnostics.disconnected();
        diagnostics.disconnected();

        VideoConnectionDiagnostics.Event disconnect = events.get(events.size() - 1);
        assertEquals(VideoConnectionDiagnostics.EventType.DISCONNECTED, disconnect.type());
        assertEquals("example.com:25565", disconnect.snapshot().address());
        assertEquals(500L, disconnect.snapshot().elapsedMillis());
        assertEquals(VideoConnectionDiagnostics.State.DISCONNECTED, diagnostics.snapshot().state());
        assertEquals("", diagnostics.snapshot().address());
        assertEquals(2, events.size());

        diagnostics.versionMismatch("late-packet");

        assertEquals(VideoConnectionDiagnostics.State.DISCONNECTED, diagnostics.snapshot().state());
        assertEquals(2, events.size());
    }
}
