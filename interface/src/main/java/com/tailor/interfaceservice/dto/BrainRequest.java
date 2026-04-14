package com.tailor.interfaceservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire-format for the Python Brain's {@code POST /tailor} endpoint.
 * Field names use snake_case to match the Brain's Pydantic model exactly.
 */
public record BrainRequest(
        @JsonProperty("resume_text")  String resumeText,
        @JsonProperty("job_description") String jobDescription
) {}
