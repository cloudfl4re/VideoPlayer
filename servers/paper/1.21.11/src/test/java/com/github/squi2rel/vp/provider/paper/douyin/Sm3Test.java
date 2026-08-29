package com.github.squi2rel.vp.provider.paper.douyin;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Sm3Test {
    @Test
    void hashesStandardVector() {
        assertEquals(
                "66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0",
                HexFormat.of().formatHex(Sm3.digest("abc".getBytes(StandardCharsets.UTF_8)))
        );
    }
}
