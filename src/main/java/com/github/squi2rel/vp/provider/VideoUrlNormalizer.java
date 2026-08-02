package com.github.squi2rel.vp.provider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VideoUrlNormalizer {
    private static final Pattern BILIBILI_VIDEO = Pattern.compile(
            "^(https://www\\.bilibili\\.com/video/BV[0-9A-Za-z]{10})/?(?:\\?([^#]*))?(#.*)?$"
    );
    private static final Pattern PAGE_VALUE = Pattern.compile("\\d{1,9}");

    private VideoUrlNormalizer() {
    }

    public static String normalizeSubmittedUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        Matcher matcher = BILIBILI_VIDEO.matcher(normalized);
        if (!matcher.matches() || (matcher.group(2) == null && matcher.group(3) == null)) return normalized;
        return matcher.group(1) + functionalQuery(matcher.group(2));
    }

    private static String functionalQuery(String query) {
        if (query == null) return "";
        for (String param : query.split("&")) {
            if (!param.startsWith("p=")) continue;
            String pageValue = param.substring(2);
            if (!PAGE_VALUE.matcher(pageValue).matches()) continue;
            int page = Integer.parseInt(pageValue);
            return page > 1 ? "?p=" + page : "";
        }
        return "";
    }
}
