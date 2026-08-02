package com.github.squi2rel.vp.video;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientResolutionStreamListenerTest {
    @Test
    void finiteClientResolutionSchedulesCompletionAfterTheDurationIsKnown() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            ClientResolutionStreamListener listener = new ClientResolutionStreamListener(scheduler);
            CountDownLatch stopped = new CountDownLatch(1);
            listener.stopped(stopped::countDown);

            listener.listen();

            assertEquals(0L, listener.getProgress());
            assertTrue(listener.resolveFinite(25L));
            assertTrue(stopped.await(1, TimeUnit.SECONDS));
            assertFalse(listener.isPlaying());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void liveClientResolutionDoesNotCreateAFiniteCompletion() {
        ClientResolutionStreamListener listener = new ClientResolutionStreamListener();

        listener.listen();

        assertTrue(listener.resolveLive());
        assertTrue(listener.isPlaying());
        assertEquals(0L, listener.getProgress());
        listener.cancel();
    }

    @Test
    void finiteClientResolutionKeepsProgressElapsedWhileTheClientWasResolving() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            ClientResolutionStreamListener listener = new ClientResolutionStreamListener(scheduler);

            listener.listen();
            Thread.sleep(30L);

            assertTrue(listener.resolveFinite(1_000L));
            assertTrue(listener.getProgress() >= 20L);
            listener.cancel();
        } finally {
            scheduler.shutdownNow();
        }
    }
}
