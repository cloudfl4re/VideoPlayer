package com.github.squi2rel.vp.provider.paper.douyin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class DouyinLivePageParser {
    private static final String PREFIX = "self.__pace_f.push([";
    private static final int MAX_CHUNK_CHARS = 2 * 1024 * 1024;
    private static final String[] QUALITIES = {"FULL_HD1", "HD1", "SD2", "SD1"};

    private DouyinLivePageParser() {
    }

    static LiveInfo parse(String html) {
        if (html == null || html.isBlank()) return null;
        int cursor = 0;
        while ((cursor = html.indexOf(PREFIX, cursor)) >= 0) {
            int comma = html.indexOf(',', cursor + PREFIX.length());
            if (comma < 0 || comma + 1 >= html.length()) return null;
            int quote = comma + 1;
            while (quote < html.length() && Character.isWhitespace(html.charAt(quote))) quote++;
            if (quote >= html.length() || html.charAt(quote) != '"') {
                cursor = comma + 1;
                continue;
            }
            int end = closingQuote(html, quote);
            if (end < 0) return null;
            if (end - quote <= MAX_CHUNK_CHARS) {
                LiveInfo info = parseChunk(html.substring(quote, end + 1));
                if (info != null) return info;
            }
            cursor = end + 1;
        }
        return null;
    }

    private static int closingQuote(String value, int start) {
        boolean escaped = false;
        for (int index = start + 1; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                return index;
            }
        }
        return -1;
    }

    private static LiveInfo parseChunk(String encoded) {
        if (!encoded.contains("roomStore")) return null;
        try {
            String decoded = JsonParser.parseString(encoded).getAsString();
            int separator = decoded.indexOf(':');
            if (separator < 0 || separator + 1 >= decoded.length()) return null;
            JsonElement root = JsonParser.parseString(decoded.substring(separator + 1));
            if (!root.isJsonArray()) return null;
            JsonArray entries = root.getAsJsonArray();
            for (JsonElement entry : entries) {
                if (!entry.isJsonObject()) continue;
                JsonObject state = object(entry.getAsJsonObject(), "state");
                JsonObject roomStore = object(state, "roomStore");
                JsonObject roomInfo = object(roomStore, "roomInfo");
                JsonObject room = object(roomInfo, "room");
                if (room == null) continue;
                int status = integer(room, "status", -1);
                String title = string(room, "title");
                JsonObject stream = object(room, "stream_url");
                String hls = quality(stream, "hls_pull_url_map");
                if (hls.isBlank()) hls = string(stream, "hls_pull_url");
                String flv = quality(stream, "flv_pull_url");
                return new LiveInfo(status, title, hls, flv);
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static String quality(JsonObject stream, String field) {
        JsonObject options = object(stream, field);
        if (options == null) return "";
        for (String quality : QUALITIES) {
            String value = string(options, quality);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static JsonObject object(JsonObject parent, String name) {
        if (parent == null || !parent.has(name)) return null;
        JsonElement value = parent.get(name);
        return value == null || value.isJsonNull() || !value.isJsonObject() ? null : value.getAsJsonObject();
    }

    private static String string(JsonObject parent, String name) {
        if (parent == null || !parent.has(name)) return "";
        JsonElement value = parent.get(name);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? "" : value.getAsString();
    }

    private static int integer(JsonObject parent, String name, int fallback) {
        if (parent == null || !parent.has(name)) return fallback;
        JsonElement value = parent.get(name);
        try {
            return value == null || value.isJsonNull() ? fallback : value.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    record LiveInfo(int status, String title, String hls, String flv) {
    }
}
