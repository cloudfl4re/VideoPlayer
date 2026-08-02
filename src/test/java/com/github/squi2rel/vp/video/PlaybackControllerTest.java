package com.github.squi2rel.vp.video;

import com.github.squi2rel.vp.network.ClientPlaybackResolution;
import com.github.squi2rel.vp.provider.VideoInfo;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackControllerTest {
    @Test
    void advancesFromFirstQueueItemToSecond() {
        Fixture fixture = new Fixture();
        VideoInfo first = info("A", "https://example.com/a.mp4", "");
        VideoInfo second = info("B", "https://example.com/b.mp4", "");

        fixture.add(first);
        fixture.add(second);
        assertSame(first, fixture.controller.currentInfo());

        fixture.listener("A").finish();

        assertSame(second, fixture.controller.currentInfo());
        assertEquals(1, fixture.queue.size());
        assertEquals(3, fixture.broadcaster.syncs);
    }

    @Test
    void removesFailedItemAndContinuesToThird() {
        Fixture fixture = new Fixture();
        VideoInfo first = info("A", "https://example.com/a.mp4", "");
        VideoInfo bad = info("bad", "", "bad-source");
        VideoInfo third = info("C", "https://example.com/c.mp4", "");

        fixture.add(first);
        fixture.add(bad);
        fixture.add(third);
        fixture.listener("A").finish();

        assertSame(third, fixture.controller.currentInfo());
        assertSame(third, fixture.queue.peek());
        assertEquals(1, fixture.queue.size());
    }

    @Test
    void keepsTransientFailureAtQueueHeadUntilTheRetryRuns() {
        ArrayList<Runnable> delayed = new ArrayList<>();
        ArrayList<FakeListener> listeners = new ArrayList<>();
        VideoArea area = new VideoArea(new Vector3f(), new Vector3f(1), "area", "world");
        area.initServer();
        area.addPlayer(UUID.randomUUID());
        VideoScreen screen = new VideoScreen(area, "screen", new Vector3f(), new Vector3f(1, 0, 0),
                new Vector3f(1, 1, 0), new Vector3f(0, 1, 0), "");
        PlaybackQueue queue = new PlaybackQueue(screen);
        PlaybackController controller = new PlaybackController(
                screen,
                queue,
                new RecordingBroadcaster(screen),
                (info, settings) -> info,
                (url, settings) -> null,
                info -> {
                    FakeListener listener = new FakeListener();
                    listeners.add(listener);
                    return listener;
                },
                Runnable::run,
                Runnable::run,
                (command, delay) -> delayed.add(command)
        );
        VideoInfo item = info("retry", "https://example.com/retry.mp4", "");

        queue.add(item);
        controller.playNext();
        listeners.getFirst().fail();

        assertEquals(1, delayed.size());
        delayed.removeFirst().run();
        assertSame(item, queue.peek());
        assertEquals(1, controller.diagnostics("").retryAttempt());
        assertTrue(controller.diagnostics("").nextRetryAtMs() >= System.currentTimeMillis());

        delayed.removeFirst().run();

        assertSame(item, queue.peek());
        assertEquals(2, listeners.size());
    }

    @Test
    void finiteClientFallbackReportsItsDurationAndAdvancesTheQueue() {
        Fixture fixture = new Fixture();
        VideoInfo first = clientFallback("fallback");
        VideoInfo second = info("B", "https://example.com/b.mp4", "");

        fixture.add(first);
        fixture.add(second);
        long generation = fixture.controller.generation();
        UUID reporter = fixture.controller.clientPlaybackReporter();
        assertNotNull(reporter);

        assertTrue(fixture.controller.acceptClientPlaybackResolution(
                reporter, generation, fixture.controller.clientPlaybackReporterToken(),
                ClientPlaybackResolution.FINITE, 1_000L
        ));
        assertSame(second, fixture.controller.currentInfo());
        assertSame(second, fixture.queue.peek());
        assertEquals(1, fixture.queue.size());
    }

    @Test
    void clientFallbackRejectsUnassignedReporterAndToken() {
        Fixture fixture = new Fixture();
        VideoInfo first = clientFallback("fallback");

        fixture.add(first);

        assertFalse(fixture.controller.acceptClientPlaybackResolution(
                UUID.randomUUID(), fixture.controller.generation(), fixture.controller.clientPlaybackReporterToken(),
                ClientPlaybackResolution.FAILED, 0L
        ));
        assertFalse(fixture.controller.acceptClientPlaybackResolution(
                fixture.controller.clientPlaybackReporter(), fixture.controller.generation(), 0L,
                ClientPlaybackResolution.FAILED, 0L
        ));
        assertSame(first, fixture.controller.currentInfo());
    }

    @Test
    void clientResolvedLiveRemainsNonSeekableWithoutCompletingTheQueueItem() {
        Fixture fixture = new Fixture();
        VideoInfo first = clientFallback("live");

        fixture.add(first);

        assertTrue(fixture.controller.acceptClientPlaybackResolution(
                fixture.controller.clientPlaybackReporter(), fixture.controller.generation(),
                fixture.controller.clientPlaybackReporterToken(), ClientPlaybackResolution.LIVE, 0L
        ));
        assertEquals(first.rawPath(), fixture.controller.currentInfo().rawPath());
        assertFalse(fixture.controller.currentInfo().seekable());
        assertSame(first, fixture.queue.peek());
    }

    @Test
    void reassignsTheReporterWhenTheOriginalReporterLeavesTheArea() {
        Fixture fixture = new Fixture();
        VideoInfo first = clientFallback("fallback");

        fixture.add(first);
        UUID firstReporter = fixture.controller.clientPlaybackReporter();
        long firstToken = fixture.controller.clientPlaybackReporterToken();
        assertNotNull(firstReporter);
        UUID replacement = UUID.randomUUID();

        fixture.area.removePlayer(firstReporter);
        fixture.controller.clientPlaybackReporterLeft(firstReporter);
        fixture.area.addPlayer(replacement);
        fixture.controller.clientPlaybackReporterAvailable();

        assertEquals(replacement, fixture.controller.clientPlaybackReporter());
        assertTrue(fixture.controller.clientPlaybackReporterToken() != 0L);
        assertFalse(fixture.controller.acceptClientPlaybackResolution(
                firstReporter, fixture.controller.generation(), firstToken, ClientPlaybackResolution.FINITE, 1_000L
        ));
    }

    @Test
    void stoppedResolutionDoesNotCreateAListener() {
        ArrayList<Runnable> resolutions = new ArrayList<>();
        AtomicInteger listeners = new AtomicInteger();
        VideoArea area = new VideoArea(new Vector3f(), new Vector3f(1), "area", "world");
        area.initServer();
        area.addPlayer(UUID.randomUUID());
        VideoScreen screen = new VideoScreen(area, "screen", new Vector3f(), new Vector3f(1, 0, 0),
                new Vector3f(1, 1, 0), new Vector3f(0, 1, 0), "");
        PlaybackQueue queue = new PlaybackQueue(screen);
        PlaybackController controller = new PlaybackController(
                screen,
                queue,
                new RecordingBroadcaster(screen),
                (info, settings) -> info,
                (url, settings) -> null,
                info -> {
                    listeners.incrementAndGet();
                    return new FakeListener();
                },
                resolutions::add,
                Runnable::run,
                (command, delay) -> command.run()
        );

        queue.add(info("A", "https://example.com/a.mp4", ""));
        controller.playNext();
        controller.stopAndClear(false);
        resolutions.getFirst().run();

        assertEquals(0, listeners.get());
    }

    @Test
    void capturesQualitySettingsBeforeAsynchronousResolution() {
        ArrayList<Runnable> resolutions = new ArrayList<>();
        AtomicReference<PlaybackController.PlaybackSettings> captured = new AtomicReference<>();
        VideoArea area = new VideoArea(new Vector3f(), new Vector3f(1), "area", "world");
        area.initServer();
        area.addPlayer(UUID.randomUUID());
        VideoScreen screen = new VideoScreen(area, "screen", new Vector3f(), new Vector3f(1, 0, 0),
                new Vector3f(1, 1, 0), new Vector3f(0, 1, 0), "");
        area.addScreen(screen);
        screen.metadata.set(ScreenMetadata.KEY_BILIBILI_QUALITY, MetaValue.ofInt(80));
        screen.metadata.set(ScreenMetadata.KEY_YOUTUBE_QUALITY, MetaValue.ofInt(720));
        PlaybackQueue queue = new PlaybackQueue(screen);
        PlaybackController controller = new PlaybackController(
                screen,
                queue,
                new RecordingBroadcaster(screen),
                (info, settings) -> {
                    captured.set(settings);
                    return info;
                },
                (url, settings) -> null,
                info -> new FakeListener(),
                resolutions::add,
                Runnable::run,
                (command, delay) -> command.run()
        );

        queue.add(info("A", "https://example.com/a.mp4", ""));
        controller.playNext();
        screen.metadata.set(ScreenMetadata.KEY_BILIBILI_QUALITY, MetaValue.ofInt(16));
        screen.metadata.set(ScreenMetadata.KEY_YOUTUBE_QUALITY, MetaValue.ofInt(360));
        resolutions.getFirst().run();

        assertEquals(80, captured.get().bilibiliQualityLimit());
        assertEquals(720, captured.get().youtubeHeightLimit());
    }

    private static VideoInfo info(String name, String path, String rawPath) {
        return new VideoInfo("player", name, path, rawPath, -1, true, new String[0], 1_000);
    }

    private static VideoInfo clientFallback(String name) {
        return new VideoInfo("player", name, "", "https://www.youtube.com/watch?v=" + name,
                -1, true, new String[0], 0);
    }

    private static final class Fixture {
        private final VideoArea area;
        private final PlaybackQueue queue;
        private final RecordingBroadcaster broadcaster;
        private final Map<String, FakeListener> listeners = new HashMap<>();
        private final PlaybackController controller;

        private Fixture() {
            area = new VideoArea(new Vector3f(), new Vector3f(1), "area", "world");
            area.initServer();
            area.addPlayer(UUID.randomUUID());
            VideoScreen screen = new VideoScreen(area, "screen", new Vector3f(), new Vector3f(1, 0, 0),
                    new Vector3f(1, 1, 0), new Vector3f(0, 1, 0), "");
            area.addScreen(screen);
            queue = new PlaybackQueue(screen);
            broadcaster = new RecordingBroadcaster(screen);
            controller = new PlaybackController(
                    screen,
                    queue,
                    broadcaster,
                    (info, settings) -> "bad-source".equals(info.rawPath()) ? null : info,
                    (url, settings) -> null,
                    info -> listeners.computeIfAbsent(info.name(), ignored ->
                            VideoListeners.awaitsClientPlaybackResolution(info)
                                    ? new ClientResolutionFakeListener()
                                    : new FakeListener()),
                    Runnable::run,
                    Runnable::run,
                    (command, delay) -> command.run()
            );
        }

        private void add(VideoInfo info) {
            queue.add(info);
            controller.playNext();
        }

        private FakeListener listener(String name) {
            return listeners.get(name);
        }
    }

    private static final class RecordingBroadcaster extends ScreenBroadcaster {
        private int syncs;

        private RecordingBroadcaster(VideoScreen screen) {
            super(screen);
        }

        @Override
        public void send(byte[] data) {
        }

        @Override
        public void sendTo(UUID uuid, byte[] data) {
        }

        @Override
        public void syncPlaylist() {
            syncs++;
        }
    }

    private static class FakeListener implements IVideoListener {
        private boolean playing;
        private Consumer<Boolean> playingListener = ignored -> {};
        private Runnable stoppedListener = () -> {};
        private Runnable erroredListener = () -> {};

        @Override
        public long getProgress() {
            return 0;
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }

        @Override
        public void playing(Consumer<Boolean> playing) {
            playingListener = playing;
        }

        @Override
        public void stopped(Runnable stopped) {
            stoppedListener = stopped;
        }

        @Override
        public void errored(Runnable errored) {
            erroredListener = errored;
        }

        @Override
        public void timeout(Runnable timeout) {
        }

        @Override
        public void listen() {
            playing = true;
            playingListener.accept(true);
        }

        @Override
        public void cancel() {
            playing = false;
        }

        protected void finish() {
            playing = false;
            stoppedListener.run();
        }

        protected void fail() {
            playing = false;
            erroredListener.run();
            stoppedListener.run();
        }
    }

    private static final class ClientResolutionFakeListener extends FakeListener implements ClientPlaybackResolutionListener {
        @Override
        public boolean resolveFinite(long durationMs) {
            if (durationMs <= 0) return false;
            finish();
            return true;
        }

        @Override
        public boolean resolveLive() {
            return true;
        }
    }
}
