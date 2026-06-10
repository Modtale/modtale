package net.modtale.exception;

public class InvalidAccountRequestException extends IllegalArgumentException {

    public InvalidAccountRequestException(String message) {
        super(message);
    }
}
