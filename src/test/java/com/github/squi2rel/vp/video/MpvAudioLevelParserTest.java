package com.github.squi2rel.vp.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MpvAudioLevelParserTest {
    @Test
    void parsesAndClampsOverallAudioLevels() {
        AudioLevelSnapshot snapshot = MpvAudioLevelParser.parse(
                "lavfi.astats.Overall.RMS_level=-18.5\nlavfi.astats.Overall.Peak_level=1.2",
                123L
        );

        assertEquals(AudioLevelSnapshot.Status.AVAILABLE, snapshot.status());
        assertEquals(-18.5f, snapshot.rmsDb());
        assertEquals(0f, snapshot.peakDb());
        assertEquals(123L, snapshot.sampledAtMs());
    }

    @Test
    void handlesSilenceMissingAndMalformedMetadata() {
        AudioLevelSnapshot silence = MpvAudioLevelParser.parse(
                "lavfi.astats.Overall.RMS_level=-inf,lavfi.astats.Overall.Peak_level=-70",
                1L
        );
        assertEquals(-60f, silence.rmsDb());
        assertEquals(-60f, silence.peakDb());
        assertEquals(AudioLevelSnapshot.Status.WAITING, MpvAudioLevelParser.parse("other=value", 2L).status());
        assertEquals(AudioLevelSnapshot.Status.WAITING, MpvAudioLevelParser.parse(
                "lavfi.astats.Overall.RMS_level=invalid",
                3L
        ).status());
        assertEquals(AudioLevelSnapshot.Status.WAITING, MpvAudioLevelParser.parse(null, 3L).status());
    }

    @Test
    void parsesMapStyleMetadataStrings() {
        AudioLevelSnapshot snapshot = MpvAudioLevelParser.parse(
                "{\"lavfi.astats.Overall.RMS_level\":\"-12.25\",\"lavfi.astats.Overall.Peak_level\":\"-3.5\"}",
                10L
        );

        assertEquals(AudioLevelSnapshot.Status.AVAILABLE, snapshot.status());
        assertEquals(-12.25f, snapshot.rmsDb());
        assertEquals(-3.5f, snapshot.peakDb());
    }
}
