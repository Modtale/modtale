package net.modtale.exception;

public class InvalidApiKeyRequestException extends IllegalArgumentException {

    public InvalidApiKeyRequestException(String message) {
        super(message);
    }
}
