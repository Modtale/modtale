package net.modtale.exception;

public class AuthenticationOperationException extends RuntimeException {

    public AuthenticationOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
