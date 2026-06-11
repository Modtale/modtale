package net.modtale.exception;

public class InvalidVersionRequestException extends IllegalArgumentException {

    public InvalidVersionRequestException(String message) {
        super(message);
    }
}
