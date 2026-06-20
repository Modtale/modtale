package net.modtale.launcher.update;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class LauncherUpdateServiceTest {

    @Test
    void selectsWindowsInstallerAsset() {
        assertEquals("Modtale Launcher-1.2.0.exe", LauncherUpdateService.compatibleAssetName(List.of(
                "modtale-launcher-1.2.0-x86_64.AppImage",
                "Modtale Launcher-1.2.0.dmg",
                "Modtale Launcher-1.2.0.exe"
        ), "Windows 11", "amd64").orElseThrow());
    }

    @Test
    void selectsMacInstallerAsset() {
        assertEquals("Modtale Launcher-1.2.0.dmg", LauncherUpdateService.compatibleAssetName(List.of(
                "modtale-launcher-1.2.0-x86_64.AppImage",
                "Modtale Launcher-1.2.0.dmg",
                "Modtale Launcher-1.2.0.exe"
        ), "Mac OS X", "aarch64").orElseThrow());
    }

    @Test
    void selectsLinuxAssetForCurrentArchitecture() {
        assertEquals("modtale-launcher-1.2.0-aarch64.AppImage", LauncherUpdateService.compatibleAssetName(List.of(
                "modtale-launcher-1.2.0-x86_64.AppImage",
                "modtale-launcher-1.2.0-aarch64.AppImage"
        ), "Linux", "aarch64").orElseThrow());
    }
}
