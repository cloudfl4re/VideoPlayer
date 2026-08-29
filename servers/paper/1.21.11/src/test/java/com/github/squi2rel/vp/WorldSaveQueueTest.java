package com.github.squi2rel.vp;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSaveQueueTest {
    @Test
    void coalescesPendingWritesForOneWorldWithoutBlockingOtherWorlds() {
        List<Runnable> scheduled = new ArrayList<>();
        List<String> writes = new ArrayList<>();
        WorldSaveQueue queue = new WorldSaveQueue(
                task -> {
                    scheduled.add(task);
                    return FoliaScheduler.TaskHandle.NONE;
                },
                snapshot -> {
                    writes.add(snapshot.dimension() + ":" + snapshot.serialized());
                    return true;
                },
                (snapshot, error) -> {
                    throw new AssertionError(error);
                }
        );

        queue.enqueue(snapshot("overworld", 1, "first"));
        queue.enqueue(snapshot("overworld", 2, "latest"));
        queue.enqueue(snapshot("nether", 1, "other"));

        assertEquals(2, scheduled.size());
        scheduled.get(0).run();
        scheduled.get(1).run();

        assertEquals(List.of("overworld:latest", "nether:other"), writes);
        assertTrue(queue.flush(1));
    }

    @Test
    void writesNewerSnapshotAfterAnAlreadyStartedWrite() {
        List<Runnable> scheduled = new ArrayList<>();
        List<String> writes = new ArrayList<>();
        AtomicReference<WorldSaveQueue> queueRef = new AtomicReference<>();
        WorldSaveQueue queue = new WorldSaveQueue(
                task -> {
                    scheduled.add(task);
                    return FoliaScheduler.TaskHandle.NONE;
                },
                snapshot -> {
                    writes.add(snapshot.serialized());
                    if (snapshot.version() == 1L) {
                        queueRef.get().enqueue(snapshot("overworld", 2, "second"));
                    }
                    return true;
                },
                (snapshot, error) -> {
                    throw new AssertionError(error);
                }
        );
        queueRef.set(queue);

        queue.enqueue(snapshot("overworld", 1, "first"));
        scheduled.getFirst().run();

        assertEquals(List.of("first", "second"), writes);
        assertTrue(queue.flush(1));
    }

    @Test
    void failureDoesNotPreventTheNextWorldSave() {
        List<Runnable> scheduled = new ArrayList<>();
        List<String> writes = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        WorldSaveQueue queue = new WorldSaveQueue(
                task -> {
                    scheduled.add(task);
                    return FoliaScheduler.TaskHandle.NONE;
                },
                snapshot -> {
                    if (snapshot.version() == 1L) throw new IllegalStateException("disk unavailable");
                    writes.add(snapshot.serialized());
                    return true;
                },
                (snapshot, error) -> failures.add(snapshot.serialized())
        );

        queue.enqueue(snapshot("overworld", 1, "failed"));
        scheduled.getFirst().run();
        queue.enqueue(snapshot("overworld", 2, "recovered"));
        scheduled.get(1).run();

        assertEquals(List.of("failed"), failures);
        assertEquals(List.of("recovered"), writes);
        assertTrue(queue.flush(1));
    }

    @Test
    void cancellationDropsQueuedSnapshotsAndReleasesTheQueue() {
        List<Runnable> scheduled = new ArrayList<>();
        List<String> writes = new ArrayList<>();
        WorldSaveQueue queue = new WorldSaveQueue(
                task -> {
                    scheduled.add(task);
                    return FoliaScheduler.TaskHandle.NONE;
                },
                snapshot -> {
                    writes.add(snapshot.serialized());
                    return true;
                },
                (snapshot, error) -> {
                    throw new AssertionError(error);
                }
        );

        queue.enqueue(snapshot("overworld", 1, "discarded"));
        queue.cancelAll();
        scheduled.getFirst().run();

        assertTrue(writes.isEmpty());
        assertTrue(queue.flush(1));
    }

    @Test
    void inlineDrainPersistsWithoutLaunchingASchedulerTask() {
        AtomicInteger launches = new AtomicInteger();
        List<String> writes = new ArrayList<>();
        WorldSaveQueue queue = new WorldSaveQueue(
                task -> {
                    launches.incrementAndGet();
                    return FoliaScheduler.TaskHandle.NONE;
                },
                snapshot -> {
                    writes.add(snapshot.serialized());
                    return true;
                },
                (snapshot, error) -> {
                    throw new AssertionError(error);
                }
        );

        WorldSaveQueue.DrainResult result = queue.drainInline(snapshot("overworld", 1, "final"));

        assertTrue(result.successful());
        assertEquals(0, launches.get());
        assertEquals(List.of("final"), writes);
        assertTrue(queue.flush(1));
    }

    @Test
    void inlineDrainSerializesWithAnActiveQueueWorker() throws Exception {
        AtomicInteger launches = new AtomicInteger();
        CountDownLatch firstWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstWrite = new CountDownLatch(1);
        List<String> writes = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            WorldSaveQueue queue = new WorldSaveQueue(
                    task -> {
                        launches.incrementAndGet();
                        var future = executor.submit(task);
                        return () -> future.cancel(true);
                    },
                    snapshot -> {
                        if (snapshot.version() == 1L) {
                            firstWriteStarted.countDown();
                            if (!releaseFirstWrite.await(2L, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("first write did not resume");
                            }
                        }
                        writes.add(snapshot.serialized());
                        return true;
                    },
                    (snapshot, error) -> {
                        throw new AssertionError(error);
                    }
            );

            queue.enqueue(snapshot("overworld", 1, "first"));
            assertTrue(firstWriteStarted.await(2L, TimeUnit.SECONDS));

            CompletableFuture<WorldSaveQueue.DrainResult> inline = CompletableFuture.supplyAsync(
                    () -> queue.drainInline(snapshot("overworld", 2, "final"))
            );
            assertFalse(inline.isDone());
            releaseFirstWrite.countDown();

            assertTrue(inline.get(2L, TimeUnit.SECONDS).successful());
            assertEquals(1, launches.get());
            assertEquals(List.of("first", "final"), writes);
            assertTrue(queue.flush(1));
        } finally {
            releaseFirstWrite.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void awaitIdleCompletesWithTheLatestWrittenSnapshot() {
        List<Runnable> scheduled = new ArrayList<>();
        WorldSaveQueue queue = new WorldSaveQueue(
                task -> {
                    scheduled.add(task);
                    return FoliaScheduler.TaskHandle.NONE;
                },
                snapshot -> true,
                (snapshot, error) -> {
                    throw new AssertionError(error);
                }
        );

        queue.enqueue(snapshot("overworld", 1, "first"));
        queue.enqueue(snapshot("overworld", 2, "latest"));
        CompletableFuture<WorldSaveQueue.DrainResult> idle = queue.awaitIdle("overworld");

        assertFalse(idle.isDone());
        scheduled.getFirst().run();

        WorldSaveQueue.DrainResult result = idle.join();
        assertTrue(result.successful());
        assertEquals("latest", result.snapshot().serialized());
    }

    @Test
    void awaitIdleReturnsTheFailedSnapshotAfterTheWriterStops() {
        List<Runnable> scheduled = new ArrayList<>();
        WorldSaveQueue queue = new WorldSaveQueue(
                task -> {
                    scheduled.add(task);
                    return FoliaScheduler.TaskHandle.NONE;
                },
                snapshot -> {
                    if (snapshot.version() == 1L) throw new IllegalStateException("disk unavailable");
                    return true;
                },
                (snapshot, error) -> {
                }
        );

        queue.enqueue(snapshot("overworld", 1, "retained"));
        scheduled.getFirst().run();

        WorldSaveQueue.DrainResult result = queue.awaitIdle("overworld").join();
        assertFalse(result.successful());
        assertNotNull(result.failure());
        assertEquals("retained", result.snapshot().serialized());
        assertFalse(queue.awaitIdle("overworld").join().successful());
        assertEquals(List.of("overworld"), queue.failedDimensions());

        queue.enqueue(snapshot("overworld", 2, "recovered"));
        scheduled.get(1).run();
        assertTrue(queue.awaitIdle("overworld").join().successful());
        assertTrue(queue.failedDimensions().isEmpty());
    }

    @Test
    void lazySnapshotSerializesOnlyInsideTheWriter() {
        List<Runnable> scheduled = new ArrayList<>();
        AtomicInteger serializations = new AtomicInteger();
        WorldSaveQueue queue = new WorldSaveQueue(
                task -> {
                    scheduled.add(task);
                    return FoliaScheduler.TaskHandle.NONE;
                },
                snapshot -> "payload".equals(snapshot.serialized()),
                (snapshot, error) -> {
                    throw new AssertionError(error);
                }
        );
        WorldSaveQueue.Snapshot lazy = WorldSaveQueue.Snapshot.lazy(
                "overworld",
                Path.of("overworld.json"),
                1L,
                1L,
                1L,
                1L,
                false,
                () -> {
                    serializations.incrementAndGet();
                    return "payload";
                }
        );

        queue.enqueue(lazy);
        assertEquals(0, serializations.get());

        scheduled.getFirst().run();
        assertEquals(1, serializations.get());
        assertEquals("payload", lazy.serialized());
        assertEquals(1, serializations.get());
    }

    @Test
    void failureListenerCannotStrandTheQueue() {
        List<Runnable> scheduled = new ArrayList<>();
        WorldSaveQueue queue = new WorldSaveQueue(
                task -> {
                    scheduled.add(task);
                    return FoliaScheduler.TaskHandle.NONE;
                },
                snapshot -> {
                    throw new IllegalStateException("disk unavailable");
                },
                (snapshot, error) -> {
                    throw new IllegalStateException("listener unavailable");
                }
        );

        queue.enqueue(snapshot("overworld", 1, "retained"));
        scheduled.getFirst().run();

        assertTrue(queue.flush(1));
        WorldSaveQueue.DrainResult result = queue.awaitIdle("overworld").join();
        assertFalse(result.successful());
        assertEquals(1, result.failure().getSuppressed().length);
    }

    private static WorldSaveQueue.Snapshot snapshot(String dimension, long version, String serialized) {
        return new WorldSaveQueue.Snapshot(
                dimension,
                Path.of(dimension + ".json"),
                1L,
                1L,
                version,
                version,
                false,
                serialized
        );
    }
}
