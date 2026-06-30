package com.simplemessenger.exception;

/**
 * Represents a single field-level validation error.
 *
 * @param field the name of the field that failed validation
 * @param issue a human-readable description of the validation failure
 */
public record FieldError(String field, String issue) {
}
