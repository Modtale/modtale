package net.modtale.exception;

public class ApiKeyOperationForbiddenException extends ForbiddenOperationException {

    public ApiKeyOperationForbiddenException(String message) {
        super(message);
    }
}
