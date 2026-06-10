package net.modtale.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequests(IllegalArgumentException ex) {
        logger.error("IllegalArgumentException:", ex);
        return ErrorMessageUtils.badRequest(ErrorMessageUtils.describe(ex, "The request could not be processed."));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex) {
        logger.error("IllegalStateException:", ex);
        return ErrorMessageUtils.badRequest(ErrorMessageUtils.describe(ex, "The request could not be completed in the current state."));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException ex) {
        logger.error("RateLimitExceededException:", ex);
        return ErrorMessageUtils.response(HttpStatus.TOO_MANY_REQUESTS,
                ErrorMessageUtils.describe(ex, "Too many requests. Please wait and try again."));
    }

    @ExceptionHandler({SecurityException.class, AccessDeniedException.class, ForbiddenOperationException.class})
    public ResponseEntity<ProblemDetail> handleSecurityExceptions(Exception ex) {
        logger.error("SecurityException:", ex);
        return ErrorMessageUtils.forbidden(ErrorMessageUtils.describe(ex, "You do not have permission to perform this action."));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorized(UnauthorizedException ex) {
        logger.error("UnauthorizedException:", ex);
        return ErrorMessageUtils.unauthorized(ErrorMessageUtils.describe(ex, "Authentication is required to perform this action."));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        logger.error("ResourceNotFoundException:", ex);
        return ErrorMessageUtils.notFound(ErrorMessageUtils.describe(ex, "The requested resource could not be found."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        logger.error("MethodArgumentNotValidException:", ex);
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("The request body did not pass validation.");
        return ErrorMessageUtils.badRequest(message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleHandlerValidation(HandlerMethodValidationException ex) {
        logger.error("HandlerMethodValidationException:", ex);
        return ErrorMessageUtils.badRequest("The request parameters did not pass validation.");
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ProblemDetail> handleUpstreamServiceException(UpstreamServiceException ex) {
        logger.error("UpstreamServiceException:", ex);
        return ErrorMessageUtils.response(ex.getStatus(),
                ErrorMessageUtils.describe(ex, "An upstream service request failed."));
    }

    @ExceptionHandler(ProjectMediaOperationException.class)
    public ResponseEntity<ProblemDetail> handleProjectMediaOperation(ProjectMediaOperationException ex) {
        logger.error("ProjectMediaOperationException:", ex);
        return ErrorMessageUtils.internalServerError(ex, "The server could not complete the requested project media operation.");
    }

    @ExceptionHandler(AuthenticationOperationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationOperation(AuthenticationOperationException ex) {
        logger.error("AuthenticationOperationException:", ex);
        return ErrorMessageUtils.internalServerError(ex, "The server could not complete the requested authentication operation.");
    }

    @ExceptionHandler(StorageOperationException.class)
    public ResponseEntity<ProblemDetail> handleStorageOperation(StorageOperationException ex) {
        logger.error("StorageOperationException:", ex);
        return ErrorMessageUtils.internalServerError(ex, ex.getFallbackMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAllOtherExceptions(Exception ex) {
        logger.error("Unhandled Exception:", ex);
        return ErrorMessageUtils.response(HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorMessageUtils.describe(ex, "The server could not complete the request."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        logger.error("MaxUploadSizeExceededException:", ex);
        String message = "File exceeds 100MB limit. Cloudflare only supports uploads up to 100MB.";
        return ErrorMessageUtils.badRequest(message);
    }
}
