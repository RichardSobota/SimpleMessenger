package com.simplemessenger.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lightweight projection of a User used inside {@link UserResponse#followers()}.
 * Intentionally does NOT contain a nested {@code followers} field to avoid circular references.
 */
public class UserSummaryResponse {

    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private OffsetDateTime createdAt;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public UserSummaryResponse() {}

    public UserSummaryResponse(UUID id, String firstName, String lastName,
                                String email, OffsetDateTime createdAt) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.createdAt = createdAt;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
