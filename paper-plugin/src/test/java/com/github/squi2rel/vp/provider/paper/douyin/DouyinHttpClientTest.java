package com.github.squi2rel.vp.provider.paper.douyin;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DouyinHttpClientTest {
    @Test
    void refreshesAnonymousSessionOnceAfterEmptyDetail() throws Exception {
        AtomicInteger sessions = new AtomicInteger();
        AtomicInteger details = new AtomicInteger();
        ArrayList<HttpRequest> detailRequests = new ArrayList<>();
        DouyinHttpClient.Exchange exchange = (request, maximumBytes) -> {
            if (request.uri().getHost().equals("ttwid.bytedance.com")) {
                int value = sessions.incrementAndGet();
                return response(200, Map.of("Set-Cookie", List.of("ttwid=session" + value + "; Path=/")), new byte[0]);
            }
            detailRequests.add(request);
            if (details.incrementAndGet() == 1) return response(200, Map.of(), new byte[0]);
            return response(200, Map.of(), "{\"status_code\":0,\"aweme_detail\":{\"desc\":\"ok\",\"video\":{}}}".getBytes(StandardCharsets.UTF_8));
        };
        DouyinHttpClient client = client(exchange);
        JsonObject detail = client.videoDetail("7670394671851523382");
        assertEquals("ok", detail.get("desc").getAsString());
        assertEquals(2, sessions.get());
        assertEquals(2, details.get());
        String query = detailRequests.get(1).uri().getRawQuery();
        assertTrue(query.contains("a_bogus="));
        assertTrue(query.contains("msToken="));
        assertTrue(query.contains("verifyFp="));
        assertTrue(detailRequests.get(1).headers().firstValue("Cookie").orElseThrow().contains("s_v_web_id="));
    }

    @Test
    void stopsAfterSingleRefresh() {
        AtomicInteger calls = new AtomicInteger();
        DouyinHttpClient.Exchange exchange = (request, maximumBytes) -> {
            calls.incrementAndGet();
            if (request.uri().getHost().equals("ttwid.bytedance.com")) {
                return response(200, Map.of("Set-Cookie", List.of("ttwid=session; Path=/")), new byte[0]);
            }
            return response(200, Map.of(), new byte[0]);
        };
        assertThrows(Exception.class, () -> client(exchange).videoDetail("7670394671851523382"));
        assertEquals(4, calls.get());
    }

    private static DouyinHttpClient client(DouyinHttpClient.Exchange exchange) {
        return new DouyinHttpClient(
                exchange,
                new SecureRandom(),
                new ABogusSigner(new SecureRandom(), () -> 1_700_000_000_000L),
                () -> 1_700_000_000_000L,
                false
        );
    }

    private static DouyinHttpClient.Response response(int status, Map<String, List<String>> headers, byte[] body) {
        return new DouyinHttpClient.Response(status, headers, body);
    }
}
