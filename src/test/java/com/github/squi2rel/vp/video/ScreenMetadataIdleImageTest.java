package com.github.squi2rel.vp.video;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenMetadataIdleImageTest {
    private static final Gson GSON = new Gson();

    @Test
    void idleImageDefaultsToEnabledWhenMissing() {
        ScreenMetadata metadata = new ScreenMetadata();

        assertTrue(metadata.getBool(ScreenMetadata.KEY_SHOW_IDLE_IMAGE, true));
    }

    @Test
    void idleImageUsesExplicitBooleanValue() {
        ScreenMetadata metadata = new ScreenMetadata();

        metadata.set(ScreenMetadata.KEY_SHOW_IDLE_IMAGE, MetaValue.ofBool(false));
        assertFalse(metadata.getBool(ScreenMetadata.KEY_SHOW_IDLE_IMAGE, true));

        metadata.set(ScreenMetadata.KEY_SHOW_IDLE_IMAGE, MetaValue.ofBool(true));
        assertTrue(metadata.getBool(ScreenMetadata.KEY_SHOW_IDLE_IMAGE, true));
    }

    @Test
    void idleImageValueSurvivesGsonRoundTrip() {
        ScreenMetadata source = new ScreenMetadata();
        source.set(ScreenMetadata.KEY_SHOW_IDLE_IMAGE, MetaValue.ofBool(false));

        ScreenMetadata restored = GSON.fromJson(GSON.toJson(source), ScreenMetadata.class);

        assertFalse(restored.getBool(ScreenMetadata.KEY_SHOW_IDLE_IMAGE, true));
    }
}
