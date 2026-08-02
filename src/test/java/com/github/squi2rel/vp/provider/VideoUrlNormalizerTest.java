package com.github.squi2rel.vp.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VideoUrlNormalizerTest {
    @Test
    void stripsBilibiliShareQueriesAndTrailingSlash() {
        assertEquals(
                "https://www.bilibili.com/video/BV1xx411c7mD",
                VideoUrlNormalizer.normalizeSubmittedUrl("https://www.bilibili.com/video/BV1xx411c7mD/?share_source=copy_web&vd_source=test")
        );
    }

    @Test
    void keepsBilibiliPageSelectionWhileStrippingFragment() {
        assertEquals(
                "https://www.bilibili.com/video/BV1xx411c7mD?p=2",
                VideoUrlNormalizer.normalizeSubmittedUrl("https://www.bilibili.com/video/BV1xx411c7mD?p=2#reply")
        );
        assertEquals(
                "https://www.bilibili.com/video/BV1xx411c7mD?p=3",
                VideoUrlNormalizer.normalizeSubmittedUrl("https://www.bilibili.com/video/BV1xx411c7mD/?p=3")
        );
    }

    @Test
    void keepsOnlyPageSelectionAmongMixedQueries() {
        assertEquals(
                "https://www.bilibili.com/video/BV1xx411c7mD?p=3",
                VideoUrlNormalizer.normalizeSubmittedUrl("https://www.bilibili.com/video/BV1xx411c7mD?p=3&spm_id_from=x")
        );
        assertEquals(
                "https://www.bilibili.com/video/BV1xx411c7mD?p=3",
                VideoUrlNormalizer.normalizeSubmittedUrl("https://www.bilibili.com/video/BV1xx411c7mD?spm_id_from=333.999&p=3&vd_source=abc")
        );
    }

    @Test
    void dropsInvalidOrDefaultPageValues() {
        assertEquals(
                "https://www.bilibili.com/video/BV1xx411c7mD",
                VideoUrlNormalizer.normalizeSubmittedUrl("https://www.bilibili.com/video/BV1xx411c7mD?p=abc")
        );
        assertEquals(
                "https://www.bilibili.com/video/BV1xx411c7mD",
                VideoUrlNormalizer.normalizeSubmittedUrl("https://www.bilibili.com/video/BV1xx411c7mD?p=")
        );
        assertEquals(
                "https://www.bilibili.com/video/BV1xx411c7mD",
                VideoUrlNormalizer.normalizeSubmittedUrl("https://www.bilibili.com/video/BV1xx411c7mD?p=1")
        );
        assertEquals(
                "https://www.bilibili.com/video/BV1xx411c7mD",
                VideoUrlNormalizer.normalizeSubmittedUrl("https://www.bilibili.com/video/BV1xx411c7mD?p=99999999999")
        );
    }

    @Test
    void normalizesPageValueCanonically() {
        assertEquals(
                "https://www.bilibili.com/video/BV1xx411c7mD?p=3",
                VideoUrlNormalizer.normalizeSubmittedUrl("https://www.bilibili.com/video/BV1xx411c7mD?p=03")
        );
    }

    @Test
    void normalizationIsIdempotent() {
        for (String url : new String[]{
                "https://www.bilibili.com/video/BV1xx411c7mD/?p=3&spm_id_from=x#reply",
                "https://www.bilibili.com/video/BV1xx411c7mD?p=03",
                "https://www.bilibili.com/video/BV1xx411c7mD/?share_source=copy_web",
                "https://www.bilibili.com/video/BV1xx411c7mD/",
                "https://example.com/video?id=1"
        }) {
            String once = VideoUrlNormalizer.normalizeSubmittedUrl(url);
            assertEquals(once, VideoUrlNormalizer.normalizeSubmittedUrl(once));
        }
    }

    @Test
    void leavesQuerylessAndNonBilibiliUrlsUnchanged() {
        assertEquals(
                "https://www.bilibili.com/video/BV1xx411c7mD/",
                VideoUrlNormalizer.normalizeSubmittedUrl("https://www.bilibili.com/video/BV1xx411c7mD/")
        );
        assertEquals(
                "https://example.com/video?id=1&p=3",
                VideoUrlNormalizer.normalizeSubmittedUrl("https://example.com/video?id=1&p=3")
        );
        assertEquals(
                "https://live.bilibili.com/123?spm_id_from=x",
                VideoUrlNormalizer.normalizeSubmittedUrl("https://live.bilibili.com/123?spm_id_from=x")
        );
    }
}
