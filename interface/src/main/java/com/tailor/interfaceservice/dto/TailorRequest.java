package com.tailor.interfaceservice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload sent by the client to initiate a tailoring session.
 * The resume arrives as a binary PDF upload (handled separately);
 * this record carries the text that has already been extracted, plus
 * the job description and the user identifier.
 */
public record TailorRequest(

        @NotBlank(message = "User ID must not be blank")
        String userId,

        @NotBlank(message = "Resume text must not be blank")
        String resumeText,

        @NotBlank(message = "Job description must not be blank")
        String jobDescription
) {}
