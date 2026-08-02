package com.github.squi2rel.vp.video;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VlcLibraryWindowsInstallTest {
    @TempDir
    Path temporary;

    @Test
    void discoversConfiguredAndStandardWindowsInstallRoots() {
        Path configured = temporary.resolve("configured");
        Path vlcHome = temporary.resolve("vlc-home");
        Path programFiles = temporary.resolve("program-files");
        Path programFilesX86 = temporary.resolve("program-files-x86");
        Path localAppData = temporary.resolve("local-app-data");

        List<Path> roots = VlcLibrary.windowsInstallRoots(Map.of(
                "VIDEOPLAYER_VLC_HOME", configured.resolve("libvlc.dll").toString(),
                "VLC_HOME", "\"" + vlcHome + "\"",
                "ProgramW6432", programFiles.toString(),
                "ProgramFiles", programFiles.toString(),
                "ProgramFiles(x86)", programFilesX86.toString(),
                "LOCALAPPDATA", localAppData.toString()
        ));

        assertEquals(List.of(
                configured.toAbsolutePath().normalize(),
                vlcHome.toAbsolutePath().normalize(),
                programFiles.resolve("VideoLAN").resolve("VLC").toAbsolutePath().normalize(),
                programFilesX86.resolve("VideoLAN").resolve("VLC").toAbsolutePath().normalize(),
                localAppData.resolve("Programs").resolve("VideoLAN").resolve("VLC").toAbsolutePath().normalize(),
                localAppData.resolve("VideoLAN").resolve("VLC").toAbsolutePath().normalize()
        ), roots);
    }

    @Test
    void readsEnvironmentNamesWithoutCaseSensitivity() {
        Path programFiles = temporary.resolve("program-files");

        assertEquals(List.of(
                programFiles.resolve("VideoLAN").resolve("VLC").toAbsolutePath().normalize()
        ), VlcLibrary.windowsInstallRoots(Map.of("programfiles", programFiles.toString())));
    }

    @Test
    void ignoresMissingEnvironment() {
        assertEquals(List.of(), VlcLibrary.windowsInstallRoots(Map.of()));
    }
}
