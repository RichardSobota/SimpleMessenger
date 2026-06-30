package com.simplemessenger.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /v1/users/{userId}}.
 * All fields are optional — only fields present in the request body are applied.
 * When a field is provided it must satisfy its constraint.
 */
public class UpdateUserRequest {

    @Size(min = 1, max = 100, message = "firstName must be between 1 and 100 characters")
    private String firstName;

    @Size(min = 1, max = 100, message = "lastName must be between 1 and 100 characters")
    private String lastName;

    @Email(message = "email must be a valid email address")
    private String email;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public UpdateUserRequest() {}

    public UpdateUserRequest(String firstName, String lastName, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returns {@code true} when no fields have been supplied (i.e. the request body is empty
     * or all fields are {@code null}).
     */
    public boolean isEmpty() {
        return firstName == null && lastName == null && email == null;
    }
}
