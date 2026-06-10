package net.modtale.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class ErrorMessageUtils {

    private static final Set<String> GENERIC_MESSAGES = Set.of(
            "internal server error",
            "request processing failed",
            "unexpected error",
            "exception",
            "runtimeexception"
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ErrorMessageUtils() {}

    public static String describe(Throwable throwable, String fallback) {
        String detail = mostSpecificMessage(throwable);
        if (detail == null || detail.isBlank()) {
            return fallback;
        }

        if (fallback == null || fallback.isBlank()) {
            return detail;
        }

        String normalizedFallback = trimTrailingPunctuation(fallback);
        if (detail.equalsIgnoreCase(fallback)
                || detail.equalsIgnoreCase(normalizedFallback)
                || detail.toLowerCase(Locale.ROOT).startsWith(normalizedFallback.toLowerCase(Locale.ROOT) + ":")) {
            return detail;
        }

        return normalizedFallback + ": " + detail;
    }

    public static ProblemDetail problemDetail(HttpStatus status, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(status.getReasonPhrase());
        detail.setProperty("error", message);
        detail.setProperty("message", message);
        return detail;
    }

    public static ResponseEntity<ProblemDetail> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(problemDetail(status, message));
    }

    public static ResponseEntity<ProblemDetail> response(HttpStatus status, Throwable throwable, String fallback) {
        return response(status, describe(throwable, fallback));
    }

    public static ResponseEntity<ProblemDetail> badRequest(String message) {
        return response(HttpStatus.BAD_REQUEST, message);
    }

    public static ResponseEntity<ProblemDetail> badRequest(Throwable throwable, String fallback) {
        return response(HttpStatus.BAD_REQUEST, throwable, fallback);
    }

    public static ResponseEntity<ProblemDetail> unauthorized(String message) {
        return response(HttpStatus.UNAUTHORIZED, message);
    }

    public static ResponseEntity<ProblemDetail> forbidden(String message) {
        return response(HttpStatus.FORBIDDEN, message);
    }

    public static ResponseEntity<ProblemDetail> forbidden(Throwable throwable, String fallback) {
        return response(HttpStatus.FORBIDDEN, throwable, fallback);
    }

    public static ResponseEntity<ProblemDetail> notFound(String message) {
        return response(HttpStatus.NOT_FOUND, message);
    }

    public static ResponseEntity<ProblemDetail> internalServerError(Throwable throwable, String fallback) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, throwable, fallback);
    }

    public static void writeJsonError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        OBJECT_MAPPER.writeValue(response.getWriter(), problemDetail(status, message));
    }

    private static String mostSpecificMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        Set<Throwable> visited = new LinkedHashSet<>();
        String fallback = null;

        Throwable current = throwable;
        while (current != null && visited.add(current)) {
            String message = normalize(current.getMessage(), current);
            if (message != null) {
                if (!isGenericMessage(message)) {
                    return message;
                }
                if (fallback == null) {
                    fallback = message;
                }
            }
            current = current.getCause();
        }

        return fallback;
    }

    private static String normalize(String message, Throwable throwable) {
        if (message == null) {
            return null;
        }

        String trimmed = message.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        if (trimmed.equals(throwable.getClass().getName()) || trimmed.equals(throwable.getClass().getSimpleName())) {
            return null;
        }

        return trimmed;
    }

    private static boolean isGenericMessage(String message) {
        return GENERIC_MESSAGES.contains(message.toLowerCase(Locale.ROOT));
    }

    private static String trimTrailingPunctuation(String message) {
        String trimmed = message.trim();
        while (!trimmed.isEmpty()) {
            char last = trimmed.charAt(trimmed.length() - 1);
            if (last == '.' || last == ':' || last == ';') {
                trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
            } else {
                break;
            }
        }
        return trimmed;
    }
}
