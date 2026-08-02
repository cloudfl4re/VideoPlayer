package com.github.squi2rel.vp.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeDependencyDiagnosticsTest {
    @Test
    void reportsMissingDependencyFromSuppressedPackageLoadFailure() {
        UnsatisfiedLinkError root = new UnsatisfiedLinkError("Unable to load library 'mpv'");
        root.addSuppressed(new UnsatisfiedLinkError(
                "/tmp/videoplayer/libmpv.so: libva.so.2: cannot open shared object file: No such file or directory"
        ));

        assertEquals("missing native dependencies: libva.so.2", NativeDependencyDiagnostics.describe(root));
        assertTrue(NativeDependencyDiagnostics.recommendation(root, "linux").contains("libva.so.2"));
    }

    @Test
    void reportsLoaderStyleMissingDependency() {
        UnsatisfiedLinkError error = new UnsatisfiedLinkError(
                "error while loading shared libraries: libpipewire-0.3.so.0: cannot open shared object file"
        );

        assertEquals("missing native dependencies: libpipewire-0.3.so.0", NativeDependencyDiagnostics.describe(error));
    }
}
