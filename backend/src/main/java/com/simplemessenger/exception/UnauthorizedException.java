package com.simplemessenger.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request lacks valid authentication credentials.
 * Maps to HTTP 401 Unauthorized.
 */
public class UnauthorizedException extends RuntimeException {

    public static final int STATUS = HttpStatus.UNAUTHORIZED.value();

    public UnauthorizedException(String message) {
        super(message);
    }

    public int getStatus() {
        return STATUS;
    }
}
