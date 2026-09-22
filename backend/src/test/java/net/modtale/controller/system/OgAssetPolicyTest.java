package net.modtale.controller.system;

import java.net.URI;
import net.modtale.config.properties.AppR2Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class OgAssetPolicyTest {
    private final OgAssetPolicy policy = new OgAssetPolicy(new AppR2Properties(null, null, null, null, "https://storage.example.test"));

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1/private", "http://169.254.169.254/", "https://cdn.modtale.net.evil.test/image",
            "https://cdn.modtale.net:444/image", "https://user@cdn.modtale.net/image", "file:///etc/passwd",
            "//attacker.test/image", "/api/v1/files/../../../actuator", "/api/v1/files/%2e%2e/%2e%2e/private", "/actuator/health"})
    void rejectsUntrustedOriginsAndUnsafeRelativePaths(String value) {
        assertNull(policy.resolve(value));
    }

    @Test
    void allowsKnownStorageOriginsAndRestrictedLocalAssetRoutes() {
        assertEquals(URI.create("https://cdn.modtale.net/icon.png"), policy.resolve("https://cdn.modtale.net/icon.png"));
        assertEquals(URI.create("https://storage.example.test/icon.png"), policy.resolve("https://storage.example.test/icon.png"));
        assertEquals(URI.create("https://modtale.net/assets/favicon.svg"), policy.resolve("/assets/favicon.svg"));
        assertEquals(URI.create("http://localhost:8080/api/v1/files/icon.png"), policy.resolve("/api/v1/files/icon.png"));
    }
}
