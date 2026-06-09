package net.modtale.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleBadRequests(IllegalArgumentException ex) {
        logger.error("IllegalArgumentException:", ex);
        return ErrorMessageUtils.badRequest(ErrorMessageUtils.describe(ex, "The request could not be processed."));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException ex) {
        logger.error("IllegalStateException:", ex);
        return ErrorMessageUtils.badRequest(ErrorMessageUtils.describe(ex, "The request could not be completed in the current state."));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<?> handleSecurityExceptions(SecurityException ex) {
        logger.error("SecurityException:", ex);
        return ErrorMessageUtils.forbidden(ErrorMessageUtils.describe(ex, "You do not have permission to perform this action."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAllOtherExceptions(Exception ex) {
        logger.error("Unhandled Exception:", ex);
        return ErrorMessageUtils.response(HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorMessageUtils.describe(ex, "The server could not complete the request."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        logger.error("MaxUploadSizeExceededException:", ex);
        String message = "File exceeds 100MB limit. Cloudflare only supports uploads up to 100MB.";
        return ErrorMessageUtils.badRequest(message);
    }
}
