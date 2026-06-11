package net.modtale.exception;

public class InvalidDownloadTokenException extends ForbiddenOperationException {

    public InvalidDownloadTokenException(String message) {
        super(message);
    }
}
