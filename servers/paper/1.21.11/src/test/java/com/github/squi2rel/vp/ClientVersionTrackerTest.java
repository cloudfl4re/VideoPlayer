package com.github.squi2rel.vp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientVersionTrackerTest {
    @Test
    void comparesNumericVersionSegments() {
        assertTrue(ClientVersionTracker.compareVersions("1.10.0", "1.9.9") > 0);
        assertTrue(ClientVersionTracker.compareVersions("2.0.1", "2.0.2") < 0);
        assertEquals(0, ClientVersionTracker.compareVersions("2.0.1", "2.0.1"));
        assertEquals(0, ClientVersionTracker.compareVersions("1.2", "1.2.0"));
    }

    @Test
    void comparesQualifiedVersionsWithoutThrowing() {
        assertTrue(ClientVersionTracker.compareVersions("2.0.1-beta", "2.0.1-alpha") > 0);
    }
}
