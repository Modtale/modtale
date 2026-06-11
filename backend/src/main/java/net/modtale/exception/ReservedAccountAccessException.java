package net.modtale.exception;

public class ReservedAccountAccessException extends UnauthorizedException {

    public ReservedAccountAccessException(String message) {
        super(message);
    }
}
