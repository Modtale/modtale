package net.modtale.launcher.hytale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HytaleApiExceptionTest {

    @Test
    void invalidGrantRequiresSignIn() {
        HytaleApiException exception = new HytaleApiException(
                "Hytale API returned HTTP 400: {\"error\":\"invalid_grant\"}",
                400,
                null
        );

        assertTrue(exception.requiresSignIn());
    }

    @Test
    void ordinaryBadRequestDoesNotRequireSignIn() {
        HytaleApiException exception = new HytaleApiException("Hytale API returned HTTP 400", 400, null);

        assertFalse(exception.requiresSignIn());
    }
}
