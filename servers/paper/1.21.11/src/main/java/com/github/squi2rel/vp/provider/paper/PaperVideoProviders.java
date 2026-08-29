package com.github.squi2rel.vp.provider.paper;

import com.github.squi2rel.vp.FoliaScheduler;
import com.github.squi2rel.vp.HttpProxyConfig;
import com.github.squi2rel.vp.provider.IVideoProvider;
import com.github.squi2rel.vp.provider.VideoProviders;
import com.github.squi2rel.vp.provider.bilibili.BiliBiliVideoProvider;
import com.github.squi2rel.vp.provider.paper.douyin.DouyinProvider;

import java.util.List;

public final class PaperVideoProviders {
    private static List<IVideoProvider> registered = List.of();

    private PaperVideoProviders() {
    }

    public static synchronized void initialize(String proxy) {
        shutdown();
        HttpProxyConfig proxyConfig = HttpProxyConfig.parse(proxy);
        ProviderAsyncExecutor executor = runnable -> {
            FoliaScheduler.TaskHandle task = FoliaScheduler.runAsync(runnable);
            return task::cancel;
        };
        B23VideoProvider b23 = new B23VideoProvider(executor, proxyConfig, new BiliBiliVideoProvider());
        DouyinProvider douyin = new DouyinProvider(executor, proxyConfig);
        registered = List.of(b23, douyin);
        VideoProviders.providers.addAll(0, registered);
    }

    public static synchronized void shutdown() {
        if (registered.isEmpty()) return;
        VideoProviders.providers.removeAll(registered);
        for (IVideoProvider provider : registered) {
            if (provider instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                }
            }
        }
        registered = List.of();
    }
}
