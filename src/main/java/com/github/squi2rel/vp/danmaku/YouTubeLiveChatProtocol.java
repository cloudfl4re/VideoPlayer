package com.github.squi2rel.vp.danmaku;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class YouTubeLiveChatProtocol {
    private static final int MAX_SEARCH_DEPTH = 64;
    private static final int MAX_AUTHOR_CODE_POINTS = 64;
    private static final int MAX_MESSAGE_CODE_POINTS = 256;
    private static final Pattern INITIAL_DATA_MARKER = Pattern.compile("(?:var\\s+|window\\s*\\[\\s*\\\")?ytInitialData(?:\\\"\\s*\\])?\\s*[=:]");

    private YouTubeLiveChatProtocol() {
    }

    static Bootstrap parseBootstrap(String html) {
        if (html == null || html.isBlank()) throw new IllegalArgumentException("YouTube page is empty");
        String apiKey = stringProperty(html, "INNERTUBE_API_KEY");
        String clientVersion = stringProperty(html, "INNERTUBE_CLIENT_VERSION");
        if (clientVersion.isBlank()) clientVersion = stringProperty(html, "clientVersion");
        JsonObject context = objectProperty(html, "INNERTUBE_CONTEXT");
        if (context == null) context = defaultContext(clientVersion, stringProperty(html, "VISITOR_DATA"));
        JsonObject initialData = initialData(html);
        String continuation = findLiveChatRendererContinuation(initialData, 0);
        if (apiKey.isBlank()) throw new IllegalArgumentException("YouTube API key is missing");
        if (clientVersion.isBlank()) throw new IllegalArgumentException("YouTube client version is missing");
        if (continuation.isBlank()) throw new IllegalArgumentException("YouTube live chat continuation is missing");
        normalizeContext(context, clientVersion);
        return new Bootstrap(apiKey, clientVersion, continuation, context);
    }

    static PollResult parsePoll(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("YouTube live chat response is empty");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject contents = object(root, "continuationContents");
        JsonObject chat = object(contents, "liveChatContinuation");
        if (chat == null) throw new IllegalArgumentException("YouTube live chat payload is missing");
        ArrayList<ChatMessage> messages = new ArrayList<>();
        JsonArray actions = array(chat, "actions");
        if (actions != null) {
            for (JsonElement actionElement : actions) {
                if (actionElement == null || !actionElement.isJsonObject()) continue;
                ChatMessage message = parseAction(actionElement.getAsJsonObject());
                if (message != null) messages.add(message);
            }
        }
        Continuation next = findPollContinuation(array(chat, "continuations"));
        LiveChatFilter filter = findLiveChatFilter(object(chat, "header"), 0);
        if (filter.token().isBlank()) filter = findLiveChatFilter(chat, 0);
        return new PollResult(next.token(), next.timeoutMs(), filter.token(), filter.selected(), List.copyOf(messages));
    }

    static JsonObject requestBody(Bootstrap bootstrap, String continuation) {
        if (bootstrap == null) throw new IllegalArgumentException("bootstrap is required");
        JsonObject body = new JsonObject();
        body.add("context", bootstrap.contextCopy());
        body.addProperty("continuation", continuation == null ? "" : continuation);
        return body;
    }

    static long pollingDelay(long timeoutMs) {
        return Math.clamp(timeoutMs <= 0 ? 1000L : timeoutMs, 250L, 10_000L);
    }

    private static ChatMessage parseAction(JsonObject action) {
        JsonObject add = object(action, "addChatItemAction");
        JsonObject item = object(add, "item");
        JsonObject renderer = object(item, "liveChatTextMessageRenderer");
        if (renderer == null) return null;
        String author = clean(text(object(renderer, "authorName")), MAX_AUTHOR_CODE_POINTS);
        String content = clean(text(object(renderer, "message")), MAX_MESSAGE_CODE_POINTS);
        if (author.isBlank() || content.isBlank()) return null;
        String id = string(renderer, "id");
        if (id.isBlank()) {
            id = Integer.toUnsignedString(string(renderer, "timestampUsec").hashCode()) + ":"
                    + Integer.toUnsignedString((author + '\u0000' + content).hashCode());
        } else if (id.length() > 256) {
            id = Integer.toUnsignedString(id.hashCode());
        }
        return new ChatMessage(id, author, content, author + ": " + content);
    }

    private static String text(JsonObject value) {
        if (value == null) return "";
        String simple = string(value, "simpleText");
        if (!simple.isBlank()) return simple;
        JsonArray runs = array(value, "runs");
        if (runs == null) return "";
        StringBuilder result = new StringBuilder();
        for (JsonElement runElement : runs) {
            if (runElement == null || !runElement.isJsonObject()) continue;
            JsonObject run = runElement.getAsJsonObject();
            String runText = string(run, "text");
            if (!runText.isEmpty()) {
                result.append(runText);
                continue;
            }
            JsonObject emoji = object(run, "emoji");
            JsonArray shortcuts = array(emoji, "shortcuts");
            if (shortcuts != null && !shortcuts.isEmpty() && shortcuts.get(0).isJsonPrimitive()) {
                result.append(shortcuts.get(0).getAsString());
            }
        }
        return result.toString();
    }

    private static String clean(String value, int maxCodePoints) {
        if (value == null || value.isBlank()) return "";
        StringBuilder result = new StringBuilder();
        boolean pendingSpace = false;
        int count = 0;
        for (int offset = 0; offset < value.length() && count < maxCodePoints; ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) {
                pendingSpace = !result.isEmpty();
                continue;
            }
            if (pendingSpace) {
                result.append(' ');
                count++;
                if (count >= maxCodePoints) break;
            }
            result.appendCodePoint(codePoint);
            pendingSpace = false;
            count++;
        }
        return result.toString().trim();
    }

    private static JsonObject initialData(String html) {
        Matcher matcher = INITIAL_DATA_MARKER.matcher(html);
        while (matcher.find()) {
            String raw = balancedObject(html, matcher.end());
            if (raw.isBlank()) continue;
            try {
                JsonObject object = JsonParser.parseString(raw).getAsJsonObject();
                if (!findLiveChatRendererContinuation(object, 0).isBlank()) {
                    return object;
                }
            } catch (RuntimeException ignored) {
            }
        }
        throw new IllegalArgumentException("YouTube initial data is missing");
    }

    private static LiveChatFilter findLiveChatFilter(JsonElement element, int depth) {
        if (element == null || element.isJsonNull() || depth > MAX_SEARCH_DEPTH) return LiveChatFilter.EMPTY;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                LiveChatFilter found = findLiveChatFilter(child, depth + 1);
                if (!found.token().isBlank()) return found;
            }
            return LiveChatFilter.EMPTY;
        }
        if (!element.isJsonObject()) return LiveChatFilter.EMPTY;
        JsonObject object = element.getAsJsonObject();
        JsonObject menu = object(object, "sortFilterSubMenuRenderer");
        JsonArray items = array(menu, "subMenuItems");
        if (items != null) {
            for (JsonElement itemElement : items) {
                if (itemElement == null || !itemElement.isJsonObject()) continue;
                JsonObject item = itemElement.getAsJsonObject();
                String title = clean(textValue(item.get("title")), 32).toLowerCase(Locale.ROOT);
                if (!title.equals("live chat")) continue;
                String continuation = findContinuation(item, 0);
                if (!continuation.isBlank()) return new LiveChatFilter(continuation, booleanValue(item, "selected"));
            }
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            LiveChatFilter found = findLiveChatFilter(entry.getValue(), depth + 1);
            if (!found.token().isBlank()) return found;
        }
        return LiveChatFilter.EMPTY;
    }

    private static String findLiveChatRendererContinuation(JsonElement element, int depth) {
        if (element == null || element.isJsonNull() || depth > MAX_SEARCH_DEPTH) return "";
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                String found = findLiveChatRendererContinuation(child, depth + 1);
                if (!found.isBlank()) return found;
            }
            return "";
        }
        if (!element.isJsonObject()) return "";
        JsonObject object = element.getAsJsonObject();
        JsonObject renderer = object(object, "liveChatRenderer");
        if (renderer != null) {
            String continuation = findContinuation(array(renderer, "continuations"), 0);
            if (!continuation.isBlank()) return continuation;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String found = findLiveChatRendererContinuation(entry.getValue(), depth + 1);
            if (!found.isBlank()) return found;
        }
        return "";
    }

    private static String findContinuation(JsonElement element, int depth) {
        if (element == null || element.isJsonNull() || depth > MAX_SEARCH_DEPTH) return "";
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                String found = findContinuation(child, depth + 1);
                if (!found.isBlank()) return found;
            }
            return "";
        }
        if (!element.isJsonObject()) return "";
        JsonObject object = element.getAsJsonObject();
        for (String key : List.of("invalidationContinuationData", "timedContinuationData", "reloadContinuationData")) {
            JsonObject data = object(object, key);
            String continuation = string(data, "continuation");
            if (!continuation.isBlank()) return continuation;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String found = findContinuation(entry.getValue(), depth + 1);
            if (!found.isBlank()) return found;
        }
        return "";
    }

    private static Continuation findPollContinuation(JsonArray continuations) {
        if (continuations == null) return Continuation.EMPTY;
        for (String key : List.of("invalidationContinuationData", "timedContinuationData", "reloadContinuationData")) {
            for (JsonElement element : continuations) {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject data = object(element.getAsJsonObject(), key);
                String token = string(data, "continuation");
                if (token.isBlank()) continue;
                return new Continuation(token, longValue(data, "timeoutMs"));
            }
        }
        return Continuation.EMPTY;
    }

    private static String textValue(JsonElement element) {
        if (element == null || element.isJsonNull()) return "";
        if (element.isJsonPrimitive()) return element.getAsString();
        return element.isJsonObject() ? text(element.getAsJsonObject()) : "";
    }

    private static JsonObject defaultContext(String clientVersion, String visitorData) {
        JsonObject client = new JsonObject();
        client.addProperty("clientName", "WEB");
        client.addProperty("clientVersion", clientVersion);
        client.addProperty("hl", "en");
        if (!visitorData.isBlank()) client.addProperty("visitorData", visitorData);
        JsonObject context = new JsonObject();
        context.add("client", client);
        return context;
    }

    private static void normalizeContext(JsonObject context, String clientVersion) {
        JsonObject client = object(context, "client");
        if (client == null) {
            client = new JsonObject();
            context.add("client", client);
        }
        client.addProperty("clientName", "WEB");
        client.addProperty("clientVersion", clientVersion);
        client.addProperty("hl", "en");
    }

    private static String stringProperty(String source, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(\\\"(?:\\\\.|[^\\\"])*\\\")");
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) return "";
        try {
            return JsonParser.parseString(matcher.group(1)).getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static JsonObject objectProperty(String source, String key) {
        String marker = "\"" + key + "\"";
        int offset = 0;
        while ((offset = source.indexOf(marker, offset)) >= 0) {
            int separator = source.indexOf(':', offset + marker.length());
            if (separator < 0 || separator - offset > marker.length() + 16) {
                offset += marker.length();
                continue;
            }
            String raw = balancedObject(source, separator + 1);
            if (!raw.isBlank()) {
                try {
                    return JsonParser.parseString(raw).getAsJsonObject();
                } catch (RuntimeException ignored) {
                }
            }
            offset += marker.length();
        }
        return null;
    }

    private static String balancedObject(String source, int offset) {
        int start = source.indexOf('{', Math.max(0, offset));
        if (start < 0 || start - offset > 64) return "";
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < source.length(); index++) {
            char current = source.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    quoted = false;
                }
                continue;
            }
            if (current == '"') {
                quoted = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return source.substring(start, index + 1);
            }
        }
        return "";
    }

    private static JsonObject object(JsonObject object, String key) {
        if (object == null || key == null) return null;
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject object, String key) {
        if (object == null || key == null) return null;
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String string(JsonObject object, String key) {
        if (object == null || key == null) return "";
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return "";
        try {
            return element.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static long longValue(JsonObject object, String key) {
        if (object == null || key == null) return 0L;
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return 0L;
        try {
            return element.getAsLong();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static boolean booleanValue(JsonObject object, String key) {
        if (object == null || key == null) return false;
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return false;
        try {
            return element.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    record Bootstrap(String apiKey, String clientVersion, String continuation, JsonObject context) {
        Bootstrap {
            context = context == null ? new JsonObject() : context.deepCopy();
        }

        JsonObject contextCopy() {
            return context.deepCopy();
        }
    }

    record ChatMessage(String id, String author, String content, String displayText) {
    }

    record PollResult(String continuation, long timeoutMs, String liveFilterContinuation,
                      boolean liveFilterSelected, List<ChatMessage> messages) {
    }

    private record Continuation(String token, long timeoutMs) {
        private static final Continuation EMPTY = new Continuation("", 0L);
    }

    private record LiveChatFilter(String token, boolean selected) {
        private static final LiveChatFilter EMPTY = new LiveChatFilter("", false);
    }
}
