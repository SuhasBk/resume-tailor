package com.tailor.interfaceservice.controller;

import com.tailor.interfaceservice.dto.PdfUploadResponse;
import com.tailor.interfaceservice.service.PdfParsingService;
import com.tailor.interfaceservice.service.TailorSessionService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

/**
 * Ingestion and tailoring entrypoints.
 *
 * <table>
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>POST</td><td>/api/v1/resume/upload</td><td>Upload a PDF and receive its extracted text</td></tr>
 *   <tr><td>POST</td><td>/api/v1/resume/tailor</td><td>Run the full tailor pipeline; streams SSE tokens</td></tr>
 * </table>
 *
 * <p>Both endpoints require an authenticated principal. In the local dev profile
 * (see {@link com.tailor.interfaceservice.config.SecurityConfig}) an in-memory
 * user is provided; in production the principal comes from the JWT filter.
 */
@RestController
@RequestMapping("/api/v1/resume")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ResumeController {

    private final PdfParsingService    pdfParsingService;
    private final TailorSessionService tailorSessionService;

    // ------------------------------------------------------------------
    // PDF upload endpoint
    // ------------------------------------------------------------------

    /**
     * Accepts a multipart PDF upload, extracts plain text, and returns it for
     * client-side review. No LLM call is made at this step.
     *
     * @param file      the uploaded PDF (max size configured in application.properties)
     * @param principal the authenticated user
     * @return extracted text and character count wrapped in a {@link PdfUploadResponse}
     */
    @PostMapping(
            value    = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<PdfUploadResponse> uploadResume(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails principal) {

        log.info("PDF upload from user={}, file={}", principal.getUsername(), file.getOriginalFilename());

        String extractedText = pdfParsingService.extractText(file);

        return ResponseEntity.ok(new PdfUploadResponse(
                null,                        // sessionId not yet assigned — session created at /tailor
                extractedText,
                extractedText.length()
        ));
    }

    // ------------------------------------------------------------------
    // Streaming tailor endpoint
    // ------------------------------------------------------------------

    /**
     * Initiates the end-to-end tailoring pipeline:
     * <ol>
     *   <li>Parse the PDF (if provided as a form part)</li>
     *   <li>Persist a new {@link com.tailor.interfaceservice.entity.TailorSession}</li>
     *   <li>Open a non-blocking SSE stream to the Brain</li>
     *   <li>Relay tokens to the client as they arrive</li>
     * </ol>
     *
     * <p>The response is {@code text/event-stream}; clients should consume it
     * with the browser {@code EventSource} API or a compatible SSE client.
     *
     * @param file           optional PDF upload (required on first call)
     * @param jobDescription the target job description
     * @param principal      authenticated user
     * @return a streaming {@link Flux} of token strings
     */
    @PostMapping(
            value    = "/tailor",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<String> tailorResume(
            @RequestPart("file") MultipartFile file,
            @RequestPart("jobDescription") @NotBlank String jobDescription,
            @AuthenticationPrincipal UserDetails principal) {

        String userId = principal.getUsername();
        log.info("Tailor request: user={}, jd length={}", userId, jobDescription.length());

        String resumeText = pdfParsingService.extractText(file);

        return tailorSessionService.createSessionAndStream(userId, resumeText, jobDescription);
    }
}
