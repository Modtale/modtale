package net.modtale.exception;

public class InvalidOrganizationRequestException extends IllegalArgumentException {

    public InvalidOrganizationRequestException(String message) {
        super(message);
    }
}
