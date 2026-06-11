package net.modtale.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

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
    void problemDetailMirrorsTheMessageInBothFields() {
        ProblemDetail detail = ErrorMessageUtils.problemDetail(HttpStatus.BAD_REQUEST, "Bad request");

        assertEquals("Bad request", detail.getDetail());
        assertEquals("Bad request", detail.getProperties().get("error"));
        assertEquals("Bad request", detail.getProperties().get("message"));
    }
}
