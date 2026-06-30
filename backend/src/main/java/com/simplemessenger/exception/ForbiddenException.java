package com.simplemessenger.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated user attempts an action they are not permitted to perform.
 * Maps to HTTP 403 Forbidden.
 */
public class ForbiddenException extends RuntimeException {

    public static final int STATUS = HttpStatus.FORBIDDEN.value();

    public ForbiddenException(String message) {
        super(message);
    }

    public int getStatus() {
        return STATUS;
    }
}
