package com.github.squi2rel.vp.provider.paper.douyin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DouyinLivePageParserTest {
    private static final Gson GSON = new Gson();

    @Test
    void selectsHighestHlsQuality() {
        JsonObject stream = new JsonObject();
        JsonObject hls = new JsonObject();
        hls.addProperty("SD1", "https://cdn.example/sd.m3u8");
        hls.addProperty("FULL_HD1", "https://cdn.example/full.m3u8");
        stream.add("hls_pull_url_map", hls);
        JsonObject flv = new JsonObject();
        flv.addProperty("FULL_HD1", "https://cdn.example/full.flv");
        stream.add("flv_pull_url", flv);
        DouyinLivePageParser.LiveInfo info = DouyinLivePageParser.parse(page(2, "Room", stream));
        assertEquals(2, info.status());
        assertEquals("Room", info.title());
        assertEquals("https://cdn.example/full.m3u8", info.hls());
        assertEquals("https://cdn.example/full.flv", info.flv());
    }

    @Test
    void fallsBackToDirectHlsAndReportsOffline() {
        JsonObject stream = new JsonObject();
        stream.addProperty("hls_pull_url", "https://cdn.example/default.m3u8");
        DouyinLivePageParser.LiveInfo info = DouyinLivePageParser.parse(page(4, "Offline", stream));
        assertEquals(4, info.status());
        assertEquals("https://cdn.example/default.m3u8", info.hls());
    }

    @Test
    void rejectsMalformedPage() {
        assertNull(DouyinLivePageParser.parse("<html></html>"));
        assertNull(DouyinLivePageParser.parse("<script>self.__pace_f.push([1,\"broken])</script>"));
    }

    private static String page(int status, String title, JsonObject stream) {
        JsonObject room = new JsonObject();
        room.addProperty("status", status);
        room.addProperty("title", title);
        room.add("stream_url", stream);
        JsonObject roomInfo = new JsonObject();
        roomInfo.add("room", room);
        JsonObject roomStore = new JsonObject();
        roomStore.add("roomInfo", roomInfo);
        JsonObject state = new JsonObject();
        state.add("roomStore", roomStore);
        JsonObject entry = new JsonObject();
        entry.add("state", state);
        JsonArray array = new JsonArray();
        array.add("$");
        array.add(entry);
        String encoded = GSON.toJson("c:" + GSON.toJson(array));
        return "<script>self.__pace_f.push([1," + encoded + "])</script>";
    }
}
