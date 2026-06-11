package net.modtale.exception;

public class StorageArtifactOperationException extends StorageOperationException {

    private static final String FALLBACK = "The server could not prepare the requested version artifact.";

    public StorageArtifactOperationException(String message, Throwable cause) {
        super(message, FALLBACK, cause);
    }

    public static StorageArtifactOperationException from(Throwable cause, String fallback) {
        return new StorageArtifactOperationException(ErrorMessageUtils.describe(cause, fallback), cause);
    }
}
