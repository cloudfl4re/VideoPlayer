package com.github.squi2rel.vp.video;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioChannelModeTest {
    @Test
    void normalizesConfiguredValuesAndDefaultsToStereo() {
        assertSame(AudioChannelMode.STEREO, AudioChannelMode.normalize(null));
        assertSame(AudioChannelMode.STEREO, AudioChannelMode.normalize(""));
        assertSame(AudioChannelMode.STEREO, AudioChannelMode.normalize("surround"));
        assertSame(AudioChannelMode.STEREO, AudioChannelMode.normalize(" stereo "));
        assertSame(AudioChannelMode.AUTO, AudioChannelMode.normalize(" AUTO "));
    }

    @Test
    void exposesCanonicalConfigValues() {
        assertEquals("stereo", AudioChannelMode.STEREO.configValue());
        assertEquals("auto", AudioChannelMode.AUTO.configValue());
    }

    @Test
    void mapsMpvAudioChannelsExactly() {
        assertEquals("stereo", AudioChannelMode.STEREO.mpvAudioChannelsOption());
        assertEquals("auto-safe", AudioChannelMode.AUTO.mpvAudioChannelsOption());
    }

    @Test
    void mapsVlcInstanceOptionsExactly() {
        assertEquals(List.of("--stereo-mode=1"), AudioChannelMode.STEREO.vlcInstanceOptions());
        assertEquals(List.of(), AudioChannelMode.AUTO.vlcInstanceOptions());
    }

    @Test
    void normalizesNonStringJsonValuesAndDetectsCanonicalValues() {
        assertSame(AudioChannelMode.STEREO, AudioChannelMode.normalizeJson(null));
        assertSame(AudioChannelMode.STEREO, AudioChannelMode.normalizeJson(JsonParser.parseString("null")));
        assertSame(AudioChannelMode.STEREO, AudioChannelMode.normalizeJson(JsonParser.parseString("{}")));
        assertSame(AudioChannelMode.STEREO, AudioChannelMode.normalizeJson(JsonParser.parseString("[]")));
        assertSame(AudioChannelMode.STEREO, AudioChannelMode.normalizeJson(JsonParser.parseString("true")));
        assertSame(AudioChannelMode.STEREO, AudioChannelMode.normalizeJson(JsonParser.parseString("5")));
        assertSame(AudioChannelMode.AUTO, AudioChannelMode.normalizeJson(JsonParser.parseString("\"auto\"")));
        assertFalse(AudioChannelMode.isCanonicalJsonValue(null));
        assertTrue(AudioChannelMode.isCanonicalJsonValue(JsonParser.parseString("\"stereo\"")));
        assertTrue(AudioChannelMode.isCanonicalJsonValue(JsonParser.parseString("\"auto\"")));
        assertFalse(AudioChannelMode.isCanonicalJsonValue(JsonParser.parseString("\" AUTO \"")));
        assertFalse(AudioChannelMode.isCanonicalJsonValue(JsonParser.parseString("{}")));
    }
}
