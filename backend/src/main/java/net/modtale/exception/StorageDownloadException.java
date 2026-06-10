package net.modtale.exception;

public class StorageDownloadException extends StorageOperationException {

    private static final String FALLBACK = "The server could not retrieve the requested file.";

    public StorageDownloadException(String message, Throwable cause) {
        super(message, FALLBACK, cause);
    }

    public static StorageDownloadException from(Throwable cause, String fallback) {
        return new StorageDownloadException(ErrorMessageUtils.describe(cause, fallback), cause);
    }
}
