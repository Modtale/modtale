package net.modtale.service.storage;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadTokenServiceTest {

    private final DownloadTokenService downloadTokenService = new DownloadTokenService();

    @SuppressWarnings("unchecked")
    private Map<String, DownloadTokenService.DownloadToken> getTokens() {
        return (Map<String, DownloadTokenService.DownloadToken>) ReflectionTestUtils.getField(downloadTokenService, "tokens");
    }

    @Test
    void generateTokenStoresPayloadAndConsumesItOnce() {
        String token = downloadTokenService.generateToken("project-1", "1.2.3", "1.0.0", List.of("dep-a", "dep-b"));

        assertNotNull(token);
        assertTrue(downloadTokenService.getActiveTokenCount() >= 1);

        DownloadTokenService.DownloadToken result = downloadTokenService.validateAndConsume(token);

        assertNotNull(result);
        assertEquals("project-1", result.getProjectId());
        assertEquals("1.2.3", result.getVersion());
        assertEquals("1.0.0", result.getGameVersion());
        assertEquals(List.of("dep-a", "dep-b"), result.getSelectedDependencies());
        assertTrue(result.isUsed());
        assertNull(downloadTokenService.validateAndConsume(token));
        assertEquals(0, downloadTokenService.getActiveTokenCount());
    }

    @Test
    void generateTokenOverloadsCreateDistinctTokens() {
        String first = downloadTokenService.generateToken("project-1", "1.0.0");
        String second = downloadTokenService.generateToken("project-1", "1.0.0", "1.1.0");

        assertNotEquals(first, second);
        assertEquals(2, downloadTokenService.getActiveTokenCount());

        DownloadTokenService.DownloadToken basic = downloadTokenService.validateAndConsume(first);
        DownloadTokenService.DownloadToken versioned = downloadTokenService.validateAndConsume(second);

        assertNotNull(basic);
        assertNull(basic.getGameVersion());
        assertNull(basic.getSelectedDependencies());
        assertNotNull(versioned);
        assertEquals("1.1.0", versioned.getGameVersion());
    }

    @Test
    void validateAndConsumeRemovesExpiredTokens() {
        getTokens().put(
                "expired",
                new DownloadTokenService.DownloadToken("project-1", "1.0.0", null, null, Instant.now().minusSeconds(30))
        );

        assertNull(downloadTokenService.validateAndConsume("expired"));
        assertFalse(getTokens().containsKey("expired"));
    }

    @Test
    void activeTokenCountCleansUpExpiredEntries() {
        getTokens().put(
                "expired",
                new DownloadTokenService.DownloadToken("project-1", "1.0.0", null, null, Instant.now().minusSeconds(30))
        );
        getTokens().put(
                "fresh",
                new DownloadTokenService.DownloadToken("project-2", "2.0.0", null, null, Instant.now().plusSeconds(30))
        );

        assertEquals(1, downloadTokenService.getActiveTokenCount());
        assertFalse(getTokens().containsKey("expired"));
        assertTrue(getTokens().containsKey("fresh"));
    }
}
