package com.github.squi2rel.vp.provider.paper;

@FunctionalInterface
public interface ProviderAsyncExecutor {
    TaskHandle execute(Runnable runnable);

    @FunctionalInterface
    interface TaskHandle {
        void cancel();
    }
}
