package net.modtale.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class LauncherRenderSettingsTest {

    @Test
    void configuresJavaFxPulseForDetectedDisplayRefreshRate() {
        Properties properties = new Properties();

        LauncherRenderSettings.configure(properties, "Linux", OptionalInt.of(165));

        assertEquals("165", properties.getProperty(LauncherRenderSettings.JAVAFX_FRAME_RATE_PROPERTY));
        assertEquals("165", properties.getProperty(LauncherRenderSettings.JAVAFX_PULSE_PROPERTY));
        assertEquals("es2,sw", properties.getProperty(LauncherRenderSettings.PRISM_ORDER_PROPERTY));
        assertEquals("true", properties.getProperty(LauncherRenderSettings.PRISM_VSYNC_PROPERTY));
    }

    @Test
    void leavesStandardRefreshRateToJavaFxNativeDetection() {
        Properties properties = new Properties();

        LauncherRenderSettings.configure(properties, "Linux", OptionalInt.of(60));

        assertNull(properties.getProperty(LauncherRenderSettings.JAVAFX_FRAME_RATE_PROPERTY));
        assertNull(properties.getProperty(LauncherRenderSettings.JAVAFX_PULSE_PROPERTY));
        assertEquals("es2,sw", properties.getProperty(LauncherRenderSettings.PRISM_ORDER_PROPERTY));
        assertEquals("true", properties.getProperty(LauncherRenderSettings.PRISM_VSYNC_PROPERTY));
    }

    @Test
    void explicitLauncherRefreshRateOverridesDetectedDisplayRefreshRate() {
        Properties properties = new Properties();
        properties.setProperty(LauncherRenderSettings.REFRESH_RATE_OVERRIDE_PROPERTY, "240");

        LauncherRenderSettings.configure(properties, "Linux", OptionalInt.of(60));

        assertEquals("240", properties.getProperty(LauncherRenderSettings.JAVAFX_FRAME_RATE_PROPERTY));
        assertEquals("240", properties.getProperty(LauncherRenderSettings.JAVAFX_PULSE_PROPERTY));
    }

    @Test
    void respectsExistingJavaFxAndPrismSettings() {
        Properties properties = new Properties();
        properties.setProperty(LauncherRenderSettings.JAVAFX_FRAME_RATE_PROPERTY, "144");
        properties.setProperty(LauncherRenderSettings.JAVAFX_PULSE_PROPERTY, "144");
        properties.setProperty(LauncherRenderSettings.PRISM_ORDER_PROPERTY, "sw");
        properties.setProperty(LauncherRenderSettings.PRISM_VSYNC_PROPERTY, "false");

        LauncherRenderSettings.configure(properties, "Windows 11", OptionalInt.of(165));

        assertEquals("144", properties.getProperty(LauncherRenderSettings.JAVAFX_FRAME_RATE_PROPERTY));
        assertEquals("144", properties.getProperty(LauncherRenderSettings.JAVAFX_PULSE_PROPERTY));
        assertEquals("sw", properties.getProperty(LauncherRenderSettings.PRISM_ORDER_PROPERTY));
        assertEquals("false", properties.getProperty(LauncherRenderSettings.PRISM_VSYNC_PROPERTY));
    }

    @Test
    void usesPlatformGpuPipelineWithSoftwareFallback() {
        Properties windowsProperties = new Properties();
        Properties macProperties = new Properties();
        Properties linuxProperties = new Properties();

        LauncherRenderSettings.configure(windowsProperties, "Windows 11", OptionalInt.empty());
        LauncherRenderSettings.configure(macProperties, "Mac OS X", OptionalInt.empty());
        LauncherRenderSettings.configure(linuxProperties, "Linux", OptionalInt.empty());

        assertEquals("d3d,sw", windowsProperties.getProperty(LauncherRenderSettings.PRISM_ORDER_PROPERTY));
        assertEquals("es2,sw", macProperties.getProperty(LauncherRenderSettings.PRISM_ORDER_PROPERTY));
        assertEquals("es2,sw", linuxProperties.getProperty(LauncherRenderSettings.PRISM_ORDER_PROPERTY));
    }

    @Test
    void picksHighestUsableDetectedRefreshRate() {
        OptionalInt refreshRate = LauncherRenderSettings.preferredRefreshRate(0, 24, 60, 165, 1200, 144);

        assertTrue(refreshRate.isPresent());
        assertEquals(165, refreshRate.getAsInt());
    }
}
