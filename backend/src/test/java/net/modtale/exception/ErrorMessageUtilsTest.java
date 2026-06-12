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
                new IllegalArgumentException("ItsNeil17 already exists")
        );

        assertEquals("Registration failed: ItsNeil17 already exists", ErrorMessageUtils.describe(error, "Registration failed"));
    }

    @Test
    void describeAvoidsRepeatingTheFallbackPrefix() {
        IllegalArgumentException error = new IllegalArgumentException("Registration failed: ItsNeil17 already exists");

        assertEquals("Registration failed: ItsNeil17 already exists", ErrorMessageUtils.describe(error, "Registration failed."));
    }

    @Test
    void describeFallsBackWhenNoUsefulMessageExists() {
        RuntimeException error = new RuntimeException(RuntimeException.class.getName());

        assertEquals("Let's lock in", ErrorMessageUtils.describe(error, "Let's lock in"));
    }

    @Test
    void problemDetailMirrorsTheMessageInBothFields() {
        ProblemDetail detail = ErrorMessageUtils.problemDetail(HttpStatus.BAD_REQUEST, "lock in");

        assertEquals("lock in", detail.getDetail());
        assertEquals("lock in", detail.getProperties().get("error"));
        assertEquals("lock in", detail.getProperties().get("message"));
    }
}
