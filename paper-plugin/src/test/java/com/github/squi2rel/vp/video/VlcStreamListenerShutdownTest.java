package com.github.squi2rel.vp.video;

import com.sun.jna.Pointer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

class VlcStreamListenerShutdownTest {
    @AfterEach
    void tearDown() throws Exception {
        set("instance", null);
        set("loadError", null);
        set("loadAttempted", false);
        set("shutDown", false);
    }

    @Test
    void shutdownReleasesGlobalInstanceAndResetsLoadState() throws Exception {
        Pointer instance = new Pointer(17L);
        set("instance", instance);
        set("loadError", new IllegalStateException("stale"));
        set("loadAttempted", true);

        try (MockedStatic<VlcLibrary> library = mockStatic(VlcLibrary.class)) {
            VlcStreamListener.shutdown();

            library.verify(() -> VlcLibrary.releaseInstance(instance));
            library.verify(VlcLibrary::resetLoadState);
        }

        assertNull(get("instance"));
        assertNull(get("loadError"));
        assertFalse((boolean) get("loadAttempted"));
        assertFalse(VlcStreamListener.load());

        try (MockedStatic<VlcLibrary> library = mockStatic(VlcLibrary.class)) {
            VlcStreamListener.resetLoadState();
            library.verify(VlcLibrary::resetLoadState);
        }
        assertFalse((boolean) get("shutDown"));
    }

    private static Object get(String name) throws Exception {
        Field field = VlcStreamListener.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void set(String name, Object value) throws Exception {
        Field field = VlcStreamListener.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
