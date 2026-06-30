package com.simplemessenger.exception;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * Thrown when one or more request fields fail validation.
 * Maps to HTTP 400 Bad Request.
 */
public class ValidationException extends RuntimeException {

    public static final int STATUS = HttpStatus.BAD_REQUEST.value();

    private final List<FieldError> details;

    public ValidationException(List<FieldError> details) {
        super("Validation failed");
        this.details = details;
    }

    public ValidationException(String message, List<FieldError> details) {
        super(message);
        this.details = details;
    }

    public int getStatus() {
        return STATUS;
    }

    public List<FieldError> getDetails() {
        return details;
    }
}
