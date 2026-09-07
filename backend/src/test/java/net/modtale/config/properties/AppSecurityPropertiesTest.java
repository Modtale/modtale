package net.modtale.config.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppSecurityPropertiesTest {
    @Test
    void missingSecretsAreGeneratedPerConfigurationInsteadOfUsingPublicDefaults() {
        var first = properties(null);
        var second = properties("");
        assertFalse(first.preAuthSecret().isBlank());
        assertNotEquals(first.preAuthSecret(), second.preAuthSecret());
    }

    @Test
    void explicitSecretIsPreservedForMultipleInstances() {
        assertEquals("configured-secret", properties("configured-secret").preAuthSecret());
    }

    private AppSecurityProperties properties(String secret) {
        return new AppSecurityProperties(secret, 600, 120, 2, 12, 15, 120, 25, 2);
    }
}
