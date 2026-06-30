package com.simplemessenger.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full User response body returned by the User endpoints.
 * The {@code followers} list contains {@link UserSummaryResponse} entries (no nested followers).
 * The {@code password} field is intentionally omitted.
 */
public class UserResponse {

    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private OffsetDateTime createdAt;
    private List<UserSummaryResponse> followers;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public UserResponse() {}

    public UserResponse(UUID id, String firstName, String lastName,
                        String email, OffsetDateTime createdAt,
                        List<UserSummaryResponse> followers) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.createdAt = createdAt;
        this.followers = followers;
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

    public List<UserSummaryResponse> getFollowers() {
        return followers;
    }

    public void setFollowers(List<UserSummaryResponse> followers) {
        this.followers = followers;
    }
}
