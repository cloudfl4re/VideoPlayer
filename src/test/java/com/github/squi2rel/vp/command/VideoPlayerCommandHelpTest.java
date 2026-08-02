package com.github.squi2rel.vp.command;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoPlayerCommandHelpTest {
    @Test
    void entriesAreUniqueAndHaveUsageAndDetails() {
        Set<String> names = new HashSet<>();
        for (VideoPlayerCommandHelp.Entry entry : VideoPlayerCommandHelp.entries()) {
            assertTrue(names.add(entry.name().toLowerCase(Locale.ROOT)));
            assertFalse(entry.summary().isBlank());
            assertFalse(entry.details().isBlank());
        }
        assertEquals(VideoPlayerCommandHelp.entries().size(), names.size());
    }

    @Test
    void lookupIsCaseInsensitiveAndSupportsYoutubeAlias() {
        assertTrue(VideoPlayerCommandHelp.find("BILIaUTH").isPresent());
        assertTrue(VideoPlayerCommandHelp.find("youtube-auth").isPresent());
        assertTrue(VideoPlayerCommandHelp.find("YOUTUBEAuth").isPresent());
        assertFalse(VideoPlayerCommandHelp.find("missing").isPresent());
    }

    @Test
    void cookieHelpDocumentsBrowserHeaderAndNetscapeFormats() {
        VideoPlayerCommandHelp.Entry bili = VideoPlayerCommandHelp.find("biliAuth").orElseThrow();
        VideoPlayerCommandHelp.Entry youtube = VideoPlayerCommandHelp.find("youtubeAuth").orElseThrow();
        assertTrue(bili.details().contains("SESSDATA=value; bili_jct=value"));
        assertTrue(bili.details().contains("Netscape"));
        assertTrue(youtube.details().contains("cookies.txt"));
        assertTrue(youtube.details().contains("raw Cookie header"));
    }

    @Test
    void audioHelpIsUniqueAndDocumentsModesAndRestart() {
        long count = VideoPlayerCommandHelp.entries().stream()
                .filter(entry -> entry.name().equalsIgnoreCase("audio"))
                .count();
        VideoPlayerCommandHelp.Entry audio = VideoPlayerCommandHelp.find("AUDIO").orElseThrow();
        assertEquals(1, count);
        assertEquals("[stereo|auto]", audio.usage());
        assertTrue(audio.details().contains("stereo"));
        assertTrue(audio.details().contains("auto"));
        assertTrue(audio.details().contains("restart"));
    }
}
