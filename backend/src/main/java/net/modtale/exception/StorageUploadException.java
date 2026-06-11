package net.modtale.exception;

public class StorageUploadException extends StorageOperationException {

    private static final String FALLBACK = "The server could not upload the requested file.";

    public StorageUploadException(String message, Throwable cause) {
        super(message, FALLBACK, cause);
    }

    public static StorageUploadException from(Throwable cause, String fallback) {
        return new StorageUploadException(ErrorMessageUtils.describe(cause, fallback), cause);
    }
}
