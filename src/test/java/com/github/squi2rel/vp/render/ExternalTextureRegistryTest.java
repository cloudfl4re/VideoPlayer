package com.github.squi2rel.vp.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalTextureRegistryTest {
    @Test
    void reusesActiveRegistrationForSameRawTexture() {
        ExternalTextureRegistry registry = new ExternalTextureRegistry();

        ExternalTextureRegistry.Acquisition first = registry.acquire(7);
        ExternalTextureRegistry.Acquisition second = registry.acquire(7);

        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(first.registration(), second.registration());
        assertEquals(1, registry.size());
    }

    @Test
    void releaseRemovesActiveRegistration() {
        ExternalTextureRegistry registry = new ExternalTextureRegistry();
        ExternalTextureRegistry.Registration registration = registry.acquire(7).registration();

        assertEquals(registration, registry.release(7).orElseThrow());
        assertEquals(0, registry.size());
    }

    @Test
    void rawTextureReuseGetsNewGenerationAndPath() {
        ExternalTextureRegistry registry = new ExternalTextureRegistry();
        ExternalTextureRegistry.Registration first = registry.acquire(7).registration();
        registry.release(7);

        ExternalTextureRegistry.Registration second = registry.acquire(7).registration();

        assertNotEquals(first.generation(), second.generation());
        assertNotEquals(first.identifierPath(), second.identifierPath());
    }

    @Test
    void clearReturnsAndRemovesAllActiveRegistrations() {
        ExternalTextureRegistry registry = new ExternalTextureRegistry();
        ExternalTextureRegistry.Registration first = registry.acquire(7).registration();
        ExternalTextureRegistry.Registration second = registry.acquire(9).registration();

        List<ExternalTextureRegistry.Registration> cleared = registry.clear();

        assertEquals(List.of(first, second), cleared);
        assertEquals(0, registry.size());
    }

    @Test
    void repeatedReleaseIsIdempotent() {
        ExternalTextureRegistry registry = new ExternalTextureRegistry();
        registry.acquire(7);

        assertTrue(registry.release(7).isPresent());
        assertTrue(registry.release(7).isEmpty());
        assertEquals(0, registry.size());
    }
}
