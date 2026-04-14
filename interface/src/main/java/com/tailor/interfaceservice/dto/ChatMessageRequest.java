package com.tailor.interfaceservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A follow-up chat message from the user.
 * The {@code sessionId} links the message to an existing {@link com.tailor.interfaceservice.entity.TailorSession}
 * so that the service can hydrate the full context (previous resume + LaTeX output).
 */
public record ChatMessageRequest(

        @NotNull(message = "Session ID is required for follow-up messages")
        Long sessionId,

        @NotBlank(message = "Message must not be blank")
        String message
) {}
