package net.modtale.launcher.hytale;

public class HytaleApiException extends RuntimeException {

    private final int statusCode;
    private final long retryAfterMillis;

    public HytaleApiException(String message) {
        this(message, -1, null);
    }

    public HytaleApiException(String message, Throwable cause) {
        this(message, -1, cause);
    }

    public HytaleApiException(String message, int statusCode, Throwable cause) {
        this(message, statusCode, cause, 0);
    }

    public HytaleApiException(String message, int statusCode, Throwable cause, long retryAfterMillis) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryAfterMillis = Math.max(0, retryAfterMillis);
    }

    public int statusCode() {
        return statusCode;
    }

    public long retryAfterMillis() {
        return retryAfterMillis;
    }

    public boolean isAuthFailure() {
        return statusCode == 401 || statusCode == 403;
    }

    public boolean requiresSignIn() {
        String message = getMessage();
        return isAuthFailure()
                || (statusCode == 400 && message != null && message.contains("invalid_grant"));
    }
}
