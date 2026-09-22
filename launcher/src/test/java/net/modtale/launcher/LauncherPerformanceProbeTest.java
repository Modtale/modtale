package net.modtale.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class LauncherPerformanceProbeTest {

    @Test
    void profilesAgainstTheActualConfiguredJavaFxPulseRate() {
        Properties properties = new Properties();
        properties.setProperty(LauncherRenderSettings.JAVAFX_PULSE_PROPERTY, "165");

        assertEquals(1000.0 / 165.0, LauncherPerformanceProbe.targetFrameMillis(properties), 0.0001);
    }

    @Test
    void explicitPerformanceRateTakesPriority() {
        Properties properties = new Properties();
        properties.setProperty(LauncherRenderSettings.JAVAFX_PULSE_PROPERTY, "165");
        properties.setProperty(LauncherPerformanceProbe.PERF_REFRESH_RATE_PROPERTY, "120");

        assertEquals(1000.0 / 120.0, LauncherPerformanceProbe.targetFrameMillis(properties), 0.0001);
    }
}
