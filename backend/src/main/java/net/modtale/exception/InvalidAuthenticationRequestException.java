package net.modtale.exception;

public class InvalidAuthenticationRequestException extends IllegalArgumentException {

    public InvalidAuthenticationRequestException(String message) {
        super(message);
    }
}
