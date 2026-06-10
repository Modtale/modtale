package net.modtale.exception;

public abstract class StorageOperationException extends RuntimeException {

    private final String fallbackMessage;

    protected StorageOperationException(String message, String fallbackMessage, Throwable cause) {
        super(message, cause);
        this.fallbackMessage = fallbackMessage;
    }

    public String getFallbackMessage() {
        return fallbackMessage;
    }
}
