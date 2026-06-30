package com.simplemessenger.handler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.simplemessenger.exception.ConflictException;
import com.simplemessenger.exception.FieldError;
import com.simplemessenger.exception.ForbiddenException;
import com.simplemessenger.exception.NotFoundException;
import com.simplemessenger.exception.UnauthorizedException;
import com.simplemessenger.exception.ValidationException;

/**
 * Centralised exception-to-HTTP-response mapping for all controllers.
 * All responses are serialised as {@code application/json}.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -------------------------------------------------------------------------
    // Response body records
    // -------------------------------------------------------------------------

    /**
     * Generic error body for 401 / 403 / 404 / 409 / 500 responses.
     *
     * @param status HTTP status code
     * @param error  short error description (e.g. "Not Found")
     * @param message human-readable explanation
     */
    public record ErrorBody(int status, String error, String message) {}

    /**
     * Field-level validation error entry used inside {@link ValidationErrorBody}.
     *
     * @param field the request field that failed validation
     * @param issue description of the violation
     */
    public record ValidationError(String field, String issue) {}

    /**
     * Error body for 400 Bad Request responses with a non-empty {@code details} array.
     *
     * @param status  HTTP status code (400)
     * @param error   "Bad Request"
     * @param message top-level message
     * @param details one or more field-level errors
     */
    public record ValidationErrorBody(int status, String error, String message, List<ValidationError> details) {}

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    /**
     * 400 — domain {@link ValidationException} (thrown by service / controller layer).
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ValidationErrorBody> handleValidationException(ValidationException ex) {
        List<ValidationError> details = mapFieldErrors(ex.getDetails());
        if (details.isEmpty()) {
            details = List.of(new ValidationError("request", "Validation failed"));
        }
        ValidationErrorBody body = new ValidationErrorBody(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                details
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * 400 — {@link MethodArgumentNotValidException} raised by Spring's {@code @Valid}.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorBody> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        List<ValidationError> details = bindingResult.getFieldErrors().stream()
                .map(fe -> new ValidationError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        if (details.isEmpty()) {
            details = List.of(new ValidationError("request", "Validation failed"));
        }
        ValidationErrorBody body = new ValidationErrorBody(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "Validation failed",
                details
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * 401 — {@link UnauthorizedException}.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorBody> handleUnauthorizedException(UnauthorizedException ex) {
        ErrorBody body = new ErrorBody(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                ex.getMessage()
        );
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * 403 — {@link ForbiddenException}.
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorBody> handleForbiddenException(ForbiddenException ex) {
        ErrorBody body = new ErrorBody(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                ex.getMessage()
        );
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * 404 — {@link NotFoundException}.
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorBody> handleNotFoundException(NotFoundException ex) {
        ErrorBody body = new ErrorBody(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage()
        );
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * 409 — {@link ConflictException}.
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorBody> handleConflictException(ConflictException ex) {
        ErrorBody body = new ErrorBody(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage()
        );
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * 500 — catch-all for any unhandled {@link Exception}.
     * Stack trace is logged server-side but never included in the response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        ErrorBody body = new ErrorBody(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred"
        );
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Converts domain {@link FieldError} records to handler-level {@link ValidationError} records.
     */
    private List<ValidationError> mapFieldErrors(List<FieldError> fieldErrors) {
        if (fieldErrors == null) {
            return List.of();
        }
        return fieldErrors.stream()
                .map(fe -> new ValidationError(fe.field(), fe.issue()))
                .toList();
    }
}
