package com.github.squi2rel.vp.video;

import java.util.Locale;

public final class MpvAudioLevelParser {
    private MpvAudioLevelParser() {
    }

    public static AudioLevelSnapshot parse(String metadata, long sampledAtMs) {
        if (metadata == null || metadata.isBlank()) return AudioLevelSnapshot.waiting();
        Float rms = null;
        Float peak = null;
        String normalized = metadata.replace("\\n", "\n");
        for (String line : normalized.split("[\\r\\n,]+")) {
            int separator = line.indexOf('=');
            if (separator < 0) separator = line.indexOf(':');
            if (separator <= 0) continue;
            String key = cleanToken(line.substring(0, separator)).toLowerCase(Locale.ROOT);
            String value = cleanToken(line.substring(separator + 1));
            if (key.endsWith("overall.rms_level")) rms = parseDb(value);
            if (key.endsWith("overall.peak_level")) peak = parseDb(value);
        }
        if (rms == null && peak == null) return AudioLevelSnapshot.waiting();
        float safeRms = rms == null ? AudioLevelSnapshot.MIN_DB : rms;
        float safePeak = peak == null ? safeRms : peak;
        return AudioLevelSnapshot.available(safeRms, safePeak, sampledAtMs);
    }

    private static Float parseDb(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.equalsIgnoreCase("-inf")) return AudioLevelSnapshot.MIN_DB;
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String cleanToken(String value) {
        String clean = value == null ? "" : value.trim();
        int start = 0;
        int end = clean.length();
        while (start < end && isWrapper(clean.charAt(start))) start++;
        while (end > start && isWrapper(clean.charAt(end - 1))) end--;
        return clean.substring(start, end);
    }

    private static boolean isWrapper(char value) {
        return value == '"' || value == '\'' || value == '{' || value == '}' || value == '[' || value == ']';
    }
}
