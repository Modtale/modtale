package net.modtale.config.db;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import net.modtale.config.properties.AppR2Properties;
import net.modtale.config.properties.AppSeedingProperties;
import org.junit.jupiter.api.Test;

class DataSeederR2SeedLocationTest {

    private final DataSeeder seeder = new DataSeeder(
            null,
            null,
            null,
            null,
            null,
            new AppR2Properties("target-bucket", "", "", "", "https://pub-test.r2.dev"),
            new AppSeedingProperties(false, AppSeedingProperties.Mode.TEMPLATE, false, "modtale-mock-template", "", "", "", "")
    );

    @Test
    void keepsPlainObjectKeys() throws Exception {
        R2Location location = location("files/plugin/example.jar");

        assertEquals("files/plugin/example.jar", location.key());
        assertFalse(location.syntheticFallback());
    }

    @Test
    void extractsKeysFromFirstPartyPublicUrls() throws Exception {
        R2Location cdnLocation = location("https://cdn.modtale.net/files/plugin/example.jar?download=1");
        R2Location r2DevLocation = location("https://pub-test.r2.dev/files/data/example.zip");

        assertEquals("files/plugin/example.jar", cdnLocation.key());
        assertFalse(cdnLocation.syntheticFallback());
        assertEquals("files/data/example.zip", r2DevLocation.key());
        assertFalse(r2DevLocation.syntheticFallback());
    }

    @Test
    void extractsKeysFromProxyUrls() throws Exception {
        R2Location location = location("/api/files/proxy/files/plugin/example.jar");

        assertEquals("files/plugin/example.jar", location.key());
        assertFalse(location.syntheticFallback());
    }

    @Test
    void marksTemplateMockDownloadsForSyntheticFallback() throws Exception {
        R2Location location = location("https://example.test/mock-downloads/review-me-0.1.0.jar");

        assertEquals("mock-downloads/review-me-0.1.0.jar", location.key());
        assertTrue(location.syntheticFallback());
    }

    @Test
    void skipsExternalHttpUrls() throws Exception {
        assertNull(rawLocation("https://example.com/files/not-r2.jar"));
    }

    private R2Location location(String rawLocation) throws Exception {
        Object raw = rawLocation(rawLocation);
        assertNotNull(raw);
        Method key = raw.getClass().getDeclaredMethod("key");
        Method syntheticFallback = raw.getClass().getDeclaredMethod("syntheticFallback");
        key.setAccessible(true);
        syntheticFallback.setAccessible(true);
        return new R2Location((String) key.invoke(raw), (Boolean) syntheticFallback.invoke(raw));
    }

    private Object rawLocation(String rawLocation) throws Exception {
        Method method = DataSeeder.class.getDeclaredMethod("r2SeedLocation", String.class);
        method.setAccessible(true);
        return method.invoke(seeder, rawLocation);
    }

    private record R2Location(String key, boolean syntheticFallback) {
    }
}
