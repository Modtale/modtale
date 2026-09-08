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

    @Test
    void doesNotOfferArmLinuxInstallerToX64Users() {
        assertEquals("modtale-launcher-1.2.0-x86_64.AppImage", LauncherUpdateService.compatibleAssetName(List.of(
                "modtale-launcher-1.2.0-x86_64.AppImage",
                "modtale-launcher-1.2.0-arm64.AppImage"
        ), "Linux", "amd64").orElseThrow());
    }

    @Test
    void selectsArchitectureSpecificWindowsInstaller() {
        assertEquals("modtale-launcher-1.2.0-arm64.exe", LauncherUpdateService.compatibleAssetName(List.of(
                "modtale-launcher-1.2.0-x64.exe",
                "modtale-launcher-1.2.0-arm64.exe",
                "modtale-launcher-1.2.0.exe"
        ), "Windows 11", "aarch64").orElseThrow());
    }

    @Test
    void selectsArchitectureSpecificMacInstaller() {
        assertEquals("modtale-launcher-1.2.0-x64.dmg", LauncherUpdateService.compatibleAssetName(List.of(
                "modtale-launcher-1.2.0-x64.dmg",
                "modtale-launcher-1.2.0-arm64.dmg",
                "modtale-launcher-1.2.0.dmg"
        ), "Mac OS X", "x86_64").orElseThrow());
    }
}
