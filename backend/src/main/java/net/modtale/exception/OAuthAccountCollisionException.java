package net.modtale.exception;

public class OAuthAccountCollisionException extends InvalidAuthenticationRequestException {

    public OAuthAccountCollisionException(String message) {
        super(message);
    }
}
