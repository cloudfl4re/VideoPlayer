package com.github.squi2rel.vp.provider.paper.douyin;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ABogusSignerTest {
    @Test
    void matchesReferenceVector() {
        ABogusSigner signer = new ABogusSigner(new SecureRandom(), () -> 1_700_000_000_000L);
        assertEquals(
                "E7mhBdugDifihdWk56KLfY3q6-jVYmQI0SVkMD2fMPDOqL39HMY29exoIBGvXY8jwG/-IeEjy4hbT3ohrQ2y0Hwf9W0L/25ksDSkKl5Q5xSSs1X9eghgJ04qmkt5SMx2RvB-rOXmqhZHKRbp09oHmhK4b1dzFgf3qJLzND==",
                signer.sign(
                        "device_platform=webapp&aid=6383&aweme_id=7670394671851523382",
                        1_700_000_000_000L,
                        1_700_000_000_006L,
                        1234,
                        5678,
                        9012
                )
        );
    }
}
