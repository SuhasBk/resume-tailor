package com.tailor.interfaceservice.dto;

/**
 * Returned after a PDF is successfully uploaded and parsed.
 * The extracted text is echoed back so the client can review it
 * before initiating the tailoring call.
 */
public record PdfUploadResponse(
        Long sessionId,
        String extractedText,
        int characterCount
) {}
