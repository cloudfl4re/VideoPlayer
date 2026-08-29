package com.github.squi2rel.vp.provider.paper.douyin;

import com.github.squi2rel.vp.provider.paper.SubmittedUrl;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DouyinUrlResolver {
    private static final Pattern VIDEO = Pattern.compile("^/(?:video|share/video)/(\\d{15,22})(?:/.*)?$");
    private static final Pattern LIVE = Pattern.compile("^/(\\d{3,20})(?:/.*)?$");
    private final RedirectFollower redirects;

    DouyinUrlResolver(RedirectFollower redirects) {
        this.redirects = redirects;
    }

    URI submitted(String input) {
        return SubmittedUrl.first(input, DouyinUrlResolver::supportedInput);
    }

    Target resolve(URI submitted) throws Exception {
        URI target = isShort(submitted) ? redirects.follow(submitted) : submitted;
        Target classified = classify(target);
        if (classified == null) throw new IllegalArgumentException("Unsupported Douyin link");
        return classified;
    }

    static Target classify(URI uri) {
        if (uri == null || uri.getHost() == null) return null;
        String path = uri.getPath() == null ? "/" : uri.getPath();
        if (isVideoHost(uri)) {
            Matcher video = VIDEO.matcher(path);
            if (video.matches()) {
                String id = video.group(1);
                return new Target(Kind.VIDEO, id, "https://www.douyin.com/video/" + id);
            }
        }
        if (isLiveHost(uri)) {
            Matcher live = LIVE.matcher(path);
            if (live.matches()) {
                String id = live.group(1);
                return new Target(Kind.LIVE, id, "https://live.douyin.com/" + id);
            }
        }
        return null;
    }

    static boolean supportedInput(URI uri) {
        return isShort(uri) || classify(uri) != null;
    }

    static boolean trustedRedirect(URI uri) {
        return isShort(uri) || isVideoHost(uri) || isLiveHost(uri);
    }

    private static boolean isShort(URI uri) {
        return SubmittedUrl.host(uri, "v.douyin.com");
    }

    private static boolean isVideoHost(URI uri) {
        return SubmittedUrl.host(uri, "douyin.com", "www.douyin.com", "iesdouyin.com", "www.iesdouyin.com");
    }

    private static boolean isLiveHost(URI uri) {
        return SubmittedUrl.host(uri, "live.douyin.com");
    }

    enum Kind {
        VIDEO,
        LIVE
    }

    record Target(Kind kind, String id, String canonicalUrl) {
    }

    @FunctionalInterface
    interface RedirectFollower {
        URI follow(URI uri) throws Exception;
    }
}
