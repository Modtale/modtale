package net.modtale.exception;

public class VersionNotFoundException extends ResourceNotFoundException {

    public VersionNotFoundException(String message) {
        super(message);
    }
}
