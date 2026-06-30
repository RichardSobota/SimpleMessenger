package com.simplemessenger.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body for a single message resource.
 */
public class MessageResponse {

    private UUID id;
    private UUID user;
    private String messageText;
    private OffsetDateTime sentAt;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public MessageResponse() {}

    public MessageResponse(UUID id, UUID user, String messageText, OffsetDateTime sentAt) {
        this.id = id;
        this.user = user;
        this.messageText = messageText;
        this.sentAt = sentAt;
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

    public UUID getUser() {
        return user;
    }

    public void setUser(UUID user) {
        this.user = user;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(OffsetDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
