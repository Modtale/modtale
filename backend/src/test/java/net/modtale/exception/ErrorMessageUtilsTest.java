package net.modtale.exception;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorMessageUtilsTest {

    @Test
    void describeUsesTheMostSpecificNonGenericCause() {
        RuntimeException error = new RuntimeException(
                "Internal Server Error",
                new IllegalArgumentException("Email already exists")
        );

        assertEquals("Registration failed: Email already exists", ErrorMessageUtils.describe(error, "Registration failed"));
    }

    @Test
    void describeAvoidsRepeatingTheFallbackPrefix() {
        IllegalArgumentException error = new IllegalArgumentException("Registration failed: Email already exists");

        assertEquals("Registration failed: Email already exists", ErrorMessageUtils.describe(error, "Registration failed."));
    }

    @Test
    void describeFallsBackWhenNoUsefulMessageExists() {
        RuntimeException error = new RuntimeException(RuntimeException.class.getName());

        assertEquals("Something went wrong", ErrorMessageUtils.describe(error, "Something went wrong"));
    }

    @Test
    void errorPayloadMirrorsTheMessageInBothFields() {
        assertEquals(
                Map.of("error", "Bad request", "message", "Bad request"),
                ErrorMessageUtils.errorPayload("Bad request")
        );
    }
}
