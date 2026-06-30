package com.simplemessenger.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource does not exist.
 * Maps to HTTP 404 Not Found.
 */
public class NotFoundException extends RuntimeException {

    public static final int STATUS = HttpStatus.NOT_FOUND.value();

    public NotFoundException(String message) {
        super(message);
    }

    public int getStatus() {
        return STATUS;
    }
}
