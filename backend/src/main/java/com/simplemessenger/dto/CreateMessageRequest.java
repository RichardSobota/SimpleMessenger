package com.simplemessenger.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /v1/messages}.
 */
public class CreateMessageRequest {

    @NotBlank(message = "messageText must not be blank")
    private String messageText;

    @NotNull(message = "user must not be null")
    private UUID user;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public CreateMessageRequest() {}

    public CreateMessageRequest(String messageText, UUID user) {
        this.messageText = messageText;
        this.user = user;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public UUID getUser() {
        return user;
    }

    public void setUser(UUID user) {
        this.user = user;
    }
}
