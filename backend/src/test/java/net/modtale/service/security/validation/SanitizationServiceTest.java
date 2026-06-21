package net.modtale.service.security.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SanitizationServiceTest {

    private final SanitizationService sanitizationService = new SanitizationService();

    @Test
    void sanitizeRemovesUnsafeMarkupAndAddsNofollowToLinks() {
        String sanitized = sanitizationService.sanitize(
                "<p>Hello<script>alert(1)</script> <a href=\"https://example.com\">world</a></p>"
        );

        assertFalse(sanitized.contains("<script>"));
        assertTrue(sanitized.contains("<p>Hello "));
        assertTrue(sanitized.contains("href=\"https://example.com\""));
        assertTrue(sanitized.contains("rel=\"nofollow\""));
    }

    @Test
    void sanitizePlainTextStripsHtmlTagsAndTrimsWhitespace() {
        assertEquals("Hello world", sanitizationService.sanitizePlainText("  <div>Hello <strong>world</strong></div>  "));
    }

    @Test
    void sanitizeMethodsReturnNullForNullInput() {
        assertEquals(null, sanitizationService.sanitize(null));
        assertEquals(null, sanitizationService.sanitizePlainText(null));
    }
}
