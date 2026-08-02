package com.github.squi2rel.vp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Coalesces writes per world while allowing different worlds to persist independently.
 */
final class WorldSaveQueue {
    @FunctionalInterface
    interface TaskLauncher {
        FoliaScheduler.TaskHandle launch(Runnable task);
    }

    @FunctionalInterface
    interface Writer {
        boolean write(Snapshot snapshot) throws Exception;
    }

    @FunctionalInterface
    interface FailureListener {
        void failed(Snapshot snapshot, Throwable error);
    }

    record Snapshot(
            String dimension,
            Path path,
            long lifecycleEpoch,
            long persistenceId,
            long version,
            long saveGeneration,
            boolean finalSave,
            boolean retryAttempt,
            Payload payload
    ) {
        Snapshot(String dimension, Path path, long lifecycleEpoch, long persistenceId, long version,
                 long saveGeneration, boolean finalSave, String serialized) {
            this(dimension, path, lifecycleEpoch, persistenceId, version, saveGeneration, finalSave, false,
                    new Payload(() -> serialized));
        }

        static Snapshot lazy(String dimension, Path path, long lifecycleEpoch, long persistenceId, long version,
                             long saveGeneration, boolean finalSave, Supplier<String> serializer) {
            return new Snapshot(dimension, path, lifecycleEpoch, persistenceId, version, saveGeneration, finalSave, false,
                    new Payload(serializer));
        }

        static Snapshot lazyRetry(String dimension, Path path, long lifecycleEpoch, long persistenceId, long version,
                                  long saveGeneration, Supplier<String> serializer) {
            return new Snapshot(dimension, path, lifecycleEpoch, persistenceId, version, saveGeneration, false, true,
                    new Payload(serializer));
        }

        String serialized() {
            return payload.serialized();
        }
    }

    record DrainResult(Snapshot snapshot, Throwable failure, boolean cancelled) {
        boolean successful() {
            return failure == null && !cancelled;
        }
    }

    private final Object lock = new Object();
    private final HashMap<String, Slot> slots = new HashMap<>();
    private final HashMap<String, DrainResult> failedDrains = new HashMap<>();
    private final TaskLauncher launcher;
    private final Writer writer;
    private final FailureListener failureListener;

    WorldSaveQueue(TaskLauncher launcher, Writer writer, FailureListener failureListener) {
        this.launcher = launcher;
        this.writer = writer;
        this.failureListener = failureListener;
    }

    void enqueue(Snapshot snapshot) {
        if (snapshot == null || snapshot.dimension() == null || snapshot.path() == null || snapshot.payload() == null) return;
        Slot slot;
        boolean start = false;
        synchronized (lock) {
            failedDrains.remove(snapshot.dimension());
            slot = slots.computeIfAbsent(snapshot.dimension(), ignored -> new Slot());
            slot.pending = snapshot;
            if (!slot.running) {
                slot.running = true;
                start = true;
            }
        }
        if (start) launch(snapshot.dimension(), slot);
    }

    CompletableFuture<DrainResult> awaitIdle(String dimension) {
        synchronized (lock) {
            Slot slot = slots.get(dimension);
            if (slot == null) {
                DrainResult failed = failedDrains.get(dimension);
                return CompletableFuture.completedFuture(failed == null
                        ? new DrainResult(null, null, false)
                        : failed);
            }
            return slot.completion;
        }
    }

    boolean flush(long timeoutMillis) {
        long deadline = System.nanoTime() + Math.max(0L, timeoutMillis) * 1_000_000L;
        synchronized (lock) {
            while (!slots.isEmpty()) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0L) return false;
                try {
                    lock.wait(Math.max(1L, remainingNanos / 1_000_000L));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    List<String> failedDimensions() {
        synchronized (lock) {
            return List.copyOf(failedDrains.keySet());
        }
    }

    void cancelAll() {
        List<FoliaScheduler.TaskHandle> tasks;
        List<Slot> cancelled;
        synchronized (lock) {
            tasks = new ArrayList<>(slots.size());
            cancelled = new ArrayList<>(slots.values());
            for (Slot slot : slots.values()) {
                if (slot.task != null) tasks.add(slot.task);
            }
            slots.clear();
            failedDrains.clear();
            lock.notifyAll();
        }
        for (Slot slot : cancelled) {
            slot.completion.complete(new DrainResult(slot.latest(), null, true));
        }
        for (FoliaScheduler.TaskHandle task : tasks) {
            task.cancel();
        }
    }

    private void launch(String dimension, Slot slot) {
        FoliaScheduler.TaskHandle task;
        try {
            task = launcher.launch(() -> drain(dimension, slot));
        } catch (Throwable error) {
            Snapshot failed;
            synchronized (lock) {
                if (slots.get(dimension) != slot) return;
                failed = slot.pending;
                slot.lastSnapshot = failed;
                slot.lastFailure = error;
                slots.remove(dimension);
                DrainResult result = new DrainResult(failed, error, false);
                failedDrains.put(dimension, result);
                slot.completion.complete(result);
                lock.notifyAll();
            }
            if (failed != null) notifyFailure(failed, error);
            return;
        }
        synchronized (lock) {
            if (slots.get(dimension) == slot && slot.running) {
                slot.task = task == null ? FoliaScheduler.TaskHandle.NONE : task;
                return;
            }
        }
        if (task != null) task.cancel();
    }

    private void drain(String dimension, Slot slot) {
        while (true) {
            Snapshot next;
            synchronized (lock) {
                if (slots.get(dimension) != slot) return;
                next = slot.pending;
                slot.pending = null;
                if (next == null) {
                    slot.running = false;
                    slots.remove(dimension);
                    DrainResult result = new DrainResult(slot.lastSnapshot, slot.lastFailure, false);
                    if (slot.lastFailure == null) failedDrains.remove(dimension);
                    else failedDrains.put(dimension, result);
                    slot.completion.complete(result);
                    lock.notifyAll();
                    return;
                }
                slot.lastSnapshot = next;
                slot.lastFailure = null;
            }
            try {
                writer.write(next);
            } catch (Throwable error) {
                synchronized (lock) {
                    if (slots.get(dimension) == slot) slot.lastFailure = error;
                }
                notifyFailure(next, error);
            }
        }
    }

    private void notifyFailure(Snapshot snapshot, Throwable error) {
        try {
            failureListener.failed(snapshot, error);
        } catch (Throwable listenerError) {
            if (listenerError != error) error.addSuppressed(listenerError);
        }
    }

    private static final class Slot {
        private Snapshot pending;
        private Snapshot lastSnapshot;
        private Throwable lastFailure;
        private boolean running;
        private FoliaScheduler.TaskHandle task;
        private final CompletableFuture<DrainResult> completion = new CompletableFuture<>();

        private Snapshot latest() {
            return pending == null ? lastSnapshot : pending;
        }
    }

    static final class Payload {
        private Supplier<String> serializer;
        private volatile String serialized;

        private Payload(Supplier<String> serializer) {
            this.serializer = serializer;
        }

        private String serialized() {
            String value = serialized;
            if (value != null) return value;
            synchronized (this) {
                if (serialized == null) {
                    serialized = serializer.get();
                    serializer = null;
                }
                return serialized;
            }
        }
    }
}
