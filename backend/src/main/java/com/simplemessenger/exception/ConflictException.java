package com.simplemessenger.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request conflicts with the current state of a resource (e.g. duplicate email).
 * Maps to HTTP 409 Conflict.
 */
public class ConflictException extends RuntimeException {

    public static final int STATUS = HttpStatus.CONFLICT.value();

    public ConflictException(String message) {
        super(message);
    }

    public int getStatus() {
        return STATUS;
    }
}
