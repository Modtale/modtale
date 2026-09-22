package net.modtale.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorMessageUtilsTest {

    @Test
    void serverErrorsDoNotExposeInternalExceptionMessages() {
        RuntimeException error = new RuntimeException("Database failed at mongodb://user:password@internal-host/db");
        var response = ErrorMessageUtils.internalServerError(error, "Could not save changes.");
        assertEquals("Could not save changes.", response.getBody().getDetail());
        assertEquals("Could not save changes.", response.getBody().getProperties().get("error"));
        var unhandled = new GlobalExceptionHandler().handleAllOtherExceptions(error);
        assertEquals("The server could not complete the request.", unhandled.getBody().getDetail());
    }

    @Test
    void clientErrorsRetainActionableValidationDetails() {
        var response = ErrorMessageUtils.badRequest(new IllegalArgumentException("Name is already in use"), "Invalid name");
        assertEquals("Invalid name: Name is already in use", response.getBody().getDetail());
    }

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
