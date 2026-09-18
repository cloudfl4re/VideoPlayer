package com.github.squi2rel.vp.provider.paper;

import java.net.URI;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SubmittedUrl {
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\\\"']+", Pattern.CASE_INSENSITIVE);
    private static final String TRAILING = ".,;:!?)]}》」】，。；：！？";

    private SubmittedUrl() {
    }

    public static URI first(String input, Predicate<URI> accepted) {
        if (input == null || input.isBlank() || accepted == null) return null;
        Matcher matcher = URL.matcher(input.trim());
        while (matcher.find()) {
            String candidate = trimTrailing(matcher.group());
            try {
                URI uri = normalize(URI.create(candidate));
                if (accepted.test(uri)) return uri;
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }

    public static URI normalize(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) throw new IllegalArgumentException("invalid URL");
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw new IllegalArgumentException("unsupported URL scheme");
        if (uri.getUserInfo() != null || uri.getPort() >= 0) throw new IllegalArgumentException("URL authority is not allowed");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        try {
            return new URI("https", null, host, -1, emptyPath(uri.getRawPath()), uri.getRawQuery(), null);
        } catch (java.net.URISyntaxException error) {
            throw new IllegalArgumentException("invalid URL", error);
        }
    }

    public static boolean host(URI uri, String... values) {
        if (uri == null || uri.getHost() == null) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (host.equals(value)) return true;
        }
        return false;
    }

    private static String trimTrailing(String value) {
        int end = value.length();
        while (end > 0 && TRAILING.indexOf(value.charAt(end - 1)) >= 0) end--;
        return value.substring(0, end);
    }

    private static String emptyPath(String path) {
        return path == null || path.isBlank() ? "/" : path;
    }
}
