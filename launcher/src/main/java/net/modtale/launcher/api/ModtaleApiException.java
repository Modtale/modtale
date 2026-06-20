package net.modtale.launcher.api;

public class ModtaleApiException extends RuntimeException {

    private final int statusCode;

    public ModtaleApiException(String message) {
        this(message, -1, null);
    }

    public ModtaleApiException(String message, Throwable cause) {
        this(message, -1, cause);
    }

    public ModtaleApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}

