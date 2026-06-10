package net.modtale.exception;

public class InvalidProjectRequestException extends IllegalArgumentException {

    public InvalidProjectRequestException(String message) {
        super(message);
    }
}
