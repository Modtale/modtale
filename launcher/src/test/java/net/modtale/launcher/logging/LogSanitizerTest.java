package net.modtale.launcher.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

class LogSanitizerTest {

    @Test
    void redactsSensitiveQueryValues() {
        String sanitized = LogSanitizer.uri(URI.create(
                "https://api.modtale.net/api/v1/auth/launcher/exchange?code=secret-code&gameVersion=2026.01&token=secret-token"
        ));

        assertTrue(sanitized.contains("code=[redacted]"));
        assertTrue(sanitized.contains("token=[redacted]"));
        assertTrue(sanitized.contains("gameVersion=2026.01"));
        assertFalse(sanitized.contains("secret-code"));
        assertFalse(sanitized.contains("secret-token"));
    }

    @Test
    void redactsSensitiveJsonBodyPreviewValues() {
        String preview = LogSanitizer.bodyPreview("""
                {"message":"nope","token":"secret-token","password":"secret-password","safe":"visible"}
                """);

        assertTrue(preview.contains("\"token\":\"[redacted]\""));
        assertTrue(preview.contains("\"password\":\"[redacted]\""));
        assertTrue(preview.contains("\"safe\":\"visible\""));
        assertFalse(preview.contains("secret-token"));
        assertFalse(preview.contains("secret-password"));
    }
}
