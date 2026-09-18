package com.github.squi2rel.vp.provider.paper;

import com.github.squi2rel.vp.HttpProxyConfig;
import com.github.squi2rel.vp.i18n.VpTranslation;
import com.github.squi2rel.vp.provider.IProviderSource;
import com.github.squi2rel.vp.provider.IVideoProvider;
import com.github.squi2rel.vp.provider.VideoInfo;
import com.github.squi2rel.vp.provider.bilibili.BiliBiliVideoProvider;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class B23VideoProvider extends AsyncVideoProvider implements IVideoProvider {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final int MAX_REDIRECTS = 3;
    private final RedirectResolver redirectResolver;
    private final BiliBiliVideoProvider delegate;
    private final Cache<String, String> redirects = CacheBuilder.newBuilder()
            .maximumSize(1024)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    B23VideoProvider(ProviderAsyncExecutor executor, HttpProxyConfig proxy, BiliBiliVideoProvider delegate) {
        this(executor, new HttpRedirectResolver(proxy), delegate);
    }

    B23VideoProvider(ProviderAsyncExecutor executor, RedirectResolver redirectResolver, BiliBiliVideoProvider delegate) {
        super(executor, 16);
        this.redirectResolver = redirectResolver;
        this.delegate = delegate;
    }

    @Override
    public @Nullable CompletableFuture<VideoInfo> from(String str, IProviderSource source) {
        URI submitted = SubmittedUrl.first(str, B23VideoProvider::isB23);
        if (submitted == null) return null;
        return submit(() -> resolve(submitted, source));
    }

    private VideoInfo resolve(URI submitted, IProviderSource source) throws Exception {
        String target = redirects.getIfPresent(submitted.toString());
        if (target == null) {
            target = redirectResolver.resolve(submitted).toString();
            redirects.put(submitted.toString(), target);
        }
        if (!isSupportedVideo(URI.create(target))) {
            source.reply(VpTranslation.of(
                    "message.videoplayer.b23_unsupported",
                    "The b23.tv link does not point to a supported Bilibili video"
            ));
            return null;
        }
        CompletableFuture<VideoInfo> delegated = delegate.from(target, source);
        if (delegated == null) return null;
        VideoInfo info;
        try {
            info = delegated.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            delegated.cancel(true);
            throw error;
        }
        if (info == null || info.path().isBlank()) {
            source.reply(VpTranslation.of(
                    "message.videoplayer.b23_resolution_failed",
                    "The Bilibili short link could not be resolved on the server"
            ));
            return null;
        }
        return info;
    }

    @Override
    public void close() {
        super.close();
        redirects.invalidateAll();
        try {
            redirectResolver.close();
        } catch (Exception ignored) {
        }
    }

    static boolean isB23(URI uri) {
        return SubmittedUrl.host(uri, "b23.tv", "www.b23.tv");
    }

    static boolean isSupportedVideo(URI uri) {
        return isBilibili(uri) && BiliBiliVideoProvider.REGEX.matcher(uri.toString()).find();
    }

    private static boolean isBilibili(URI uri) {
        if (uri == null || uri.getHost() == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        return (host.equals("bilibili.com") || host.endsWith(".bilibili.com"))
                && uri.getUserInfo() == null && uri.getPort() < 0;
    }

    @FunctionalInterface
    interface RedirectResolver extends AutoCloseable {
        URI resolve(URI uri) throws Exception;

        @Override
        default void close() {
        }
    }

    private static final class HttpRedirectResolver implements RedirectResolver {
        private final HttpClient client;

        HttpRedirectResolver(HttpProxyConfig proxy) {
            this.client = proxy.configure(HttpClient.newBuilder())
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
        }

        @Override
        public URI resolve(URI submitted) throws Exception {
            URI current = submitted;
            for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
                if (isSupportedVideo(current)) return current;
                if (!isB23(current)) throw new IllegalStateException("Bilibili short link redirected to an untrusted host");
                RedirectResponse response = request(current, true);
                if (response.location() == null && (response.status() == 400 || response.status() == 403 || response.status() == 405)) {
                    response = request(current, false);
                }
                if (response.location() == null) throw new IllegalStateException("Bilibili short link did not redirect");
                URI next = SubmittedUrl.normalize(current.resolve(response.location()));
                if (!isB23(next) && !isBilibili(next)) {
                    throw new IllegalStateException("Bilibili short link redirected to an untrusted host");
                }
                current = next;
            }
            throw new IllegalStateException("Bilibili short link exceeded the redirect limit");
        }

        private RedirectResponse request(URI uri, boolean head) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,*/*");
            if (head) {
                builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Range", "bytes=0-0").GET();
            }
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                String location = response.headers().firstValue("Location").orElse(null);
                return new RedirectResponse(response.statusCode(), location);
            }
        }

        @Override
        public void close() {
            client.shutdownNow();
        }
    }

    private record RedirectResponse(int status, String location) {
    }
}
