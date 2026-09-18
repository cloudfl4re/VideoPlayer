package com.github.squi2rel.vp.provider.paper.douyin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DouyinProviderTest {
    @Test
    void selects1080pH264Before4kHevc() throws Exception {
        JsonObject detail = detail();
        JsonArray rates = detail.getAsJsonObject("video").getAsJsonArray("bit_rate");
        rates.add(rate("4k-hevc", 3840, 2160, 30, 6_000_000, true, false, "https://cdn.example/4k"));
        rates.add(rate("576-h264", 1024, 576, 30, 3_000_000, false, false, "https://cdn.example/576"));
        rates.add(rate("1080-h264", 1920, 1080, 30, 2_600_000, false, false, "https://cdn.example/1080"));
        ArrayList<List<String>> attempts = new ArrayList<>();
        DouyinProvider.ResolvedVideo resolved = DouyinProvider.parseVideo(
                detail,
                "7670394671851523382",
                "https://www.douyin.com/video/7670394671851523382",
                (urls, referer) -> {
                    attempts.add(urls);
                    return URI.create(urls.get(0));
                },
                1_000L
        );
        assertEquals("https://cdn.example/1080", resolved.path());
        assertEquals("Title", resolved.title());
        assertEquals(108_634L, resolved.durationMs());
        assertEquals(601_000L, resolved.expiresAt());
        assertEquals(List.of("https://cdn.example/1080"), attempts.get(0));
    }

    @Test
    void excludesH266AndKeepsH264AheadOfHevc() {
        JsonObject detail = detail();
        JsonArray rates = detail.getAsJsonObject("video").getAsJsonArray("bit_rate");
        rates.add(rate("bytevc2", 1920, 1080, 60, 8_000_000, false, true, "https://cdn.example/h266"));
        rates.add(rate("hevc", 1920, 1080, 60, 4_000_000, true, false, "https://cdn.example/hevc"));
        rates.add(rate("h264", 1280, 720, 30, 1_500_000, false, false, "https://cdn.example/h264"));
        List<DouyinProvider.MediaCandidate> candidates = DouyinProvider.orderedCandidates(detail.getAsJsonObject("video"));
        assertEquals("https://cdn.example/h264", candidates.get(0).urls().get(0));
        assertFalse(candidates.stream().flatMap(candidate -> candidate.urls().stream()).anyMatch(url -> url.endsWith("h266")));
    }

    private static JsonObject detail() {
        JsonObject video = new JsonObject();
        video.addProperty("duration", 108_634L);
        video.add("bit_rate", new JsonArray());
        JsonObject detail = new JsonObject();
        detail.addProperty("desc", "Title");
        detail.add("video", video);
        return detail;
    }

    private static JsonObject rate(String gear, int width, int height, int fps, long bitrate,
                                   boolean h265, boolean h266, String url) {
        JsonObject address = new JsonObject();
        address.addProperty("width", width);
        address.addProperty("height", height);
        JsonArray urls = new JsonArray();
        urls.add(url);
        address.add("url_list", urls);
        JsonObject rate = new JsonObject();
        rate.addProperty("gear_name", gear);
        rate.addProperty("FPS", fps);
        rate.addProperty("bit_rate", bitrate);
        rate.addProperty("is_h265", h265 ? 1 : 0);
        rate.addProperty("is_bytevc1", h265 ? 1 : 0);
        rate.addProperty("is_bytevc2", h266 ? 1 : 0);
        rate.add("play_addr", address);
        return rate;
    }
}
