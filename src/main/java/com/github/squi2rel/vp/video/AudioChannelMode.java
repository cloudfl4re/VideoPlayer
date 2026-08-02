package com.github.squi2rel.vp.video;

import com.google.gson.JsonElement;

import java.util.List;
import java.util.Locale;

public enum AudioChannelMode {
    STEREO("stereo", "stereo", List.of("--stereo-mode=1")),
    AUTO("auto", "auto-safe", List.of());

    private final String configValue;
    private final String mpvAudioChannelsOption;
    private final List<String> vlcInstanceOptions;

    AudioChannelMode(String configValue, String mpvAudioChannelsOption, List<String> vlcInstanceOptions) {
        this.configValue = configValue;
        this.mpvAudioChannelsOption = mpvAudioChannelsOption;
        this.vlcInstanceOptions = vlcInstanceOptions;
    }

    public String configValue() {
        return configValue;
    }

    public String mpvAudioChannelsOption() {
        return mpvAudioChannelsOption;
    }

    public List<String> vlcInstanceOptions() {
        return vlcInstanceOptions;
    }

    public static AudioChannelMode normalize(String value) {
        if (value == null) return STEREO;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return AUTO.configValue.equals(normalized) ? AUTO : STEREO;
    }

    public static AudioChannelMode normalizeJson(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return STEREO;
        return normalize(value.getAsString());
    }

    public static boolean isCanonicalJsonValue(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return false;
        String raw = value.getAsString();
        return normalize(raw).configValue.equals(raw);
    }
}
