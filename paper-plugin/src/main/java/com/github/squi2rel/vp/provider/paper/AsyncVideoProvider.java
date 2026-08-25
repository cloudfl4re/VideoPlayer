package com.github.squi2rel.vp.provider.paper;

import com.github.squi2rel.vp.provider.VideoInfo;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AsyncVideoProvider implements AutoCloseable {
    private final ProviderAsyncExecutor executor;
    private final Semaphore capacity;
    private final Set<ResolutionFuture> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean active = new AtomicBoolean(true);

    protected AsyncVideoProvider(ProviderAsyncExecutor executor, int maximumConcurrent) {
        if (executor == null) throw new IllegalArgumentException("executor is required");
        if (maximumConcurrent < 1) throw new IllegalArgumentException("maximumConcurrent must be positive");
        this.executor = executor;
        this.capacity = new Semaphore(maximumConcurrent);
    }

    protected final CompletableFuture<VideoInfo> submit(Callable<VideoInfo> resolver) {
        if (resolver == null) return CompletableFuture.failedFuture(new IllegalArgumentException("resolver is required"));
        if (!active.get()) return CompletableFuture.failedFuture(new CancellationException("provider is stopped"));
        if (!capacity.tryAcquire()) {
            return CompletableFuture.failedFuture(new IllegalStateException("provider resolution capacity is full"));
        }
        ResolutionFuture future = new ResolutionFuture();
        inFlight.add(future);
        try {
            ProviderAsyncExecutor.TaskHandle handle = executor.execute(() -> runResolver(future, resolver));
            future.bind(handle);
        } catch (Throwable error) {
            inFlight.remove(future);
            capacity.release();
            future.completeExceptionally(error);
        }
        return future;
    }

    private void runResolver(ResolutionFuture future, Callable<VideoInfo> resolver) {
        try {
            if (!active.get() || future.isCancelled()) {
                future.cancel(false);
                return;
            }
            VideoInfo resolved = resolver.call();
            if (!active.get()) {
                future.cancel(false);
            } else if (!future.isDone()) {
                future.complete(resolved);
            }
        } catch (CancellationException error) {
            future.cancel(false);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            future.cancel(false);
        } catch (Throwable error) {
            if (!future.isDone()) future.completeExceptionally(error);
        } finally {
            inFlight.remove(future);
            capacity.release();
        }
    }

    protected final boolean active() {
        return active.get();
    }

    @Override
    public void close() {
        if (!active.compareAndSet(true, false)) return;
        for (ResolutionFuture future : new ArrayList<>(inFlight)) future.cancel(true);
        inFlight.clear();
    }

    private static final class ResolutionFuture extends CompletableFuture<VideoInfo> {
        private final AtomicReference<ProviderAsyncExecutor.TaskHandle> handle = new AtomicReference<>();

        void bind(ProviderAsyncExecutor.TaskHandle value) {
            if (value == null) return;
            if (!handle.compareAndSet(null, value) || isCancelled()) value.cancel();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                ProviderAsyncExecutor.TaskHandle task = handle.get();
                if (task != null) task.cancel();
            }
            return cancelled;
        }
    }
}
