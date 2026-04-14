package com.tailor.interfaceservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * Translates domain exceptions into RFC 7807 Problem Detail responses.
 * Every error response carries a structured body so clients can
 * programmatically distinguish error categories.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ------------------------------------------------------------------
    // Domain exceptions
    // ------------------------------------------------------------------

    @ExceptionHandler(SessionNotFoundException.class)
    public ProblemDetail handleSessionNotFound(SessionNotFoundException ex) {
        log.warn("Session not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("urn:tailor:error:session-not-found"));
        return pd;
    }

    @ExceptionHandler(PdfParsingException.class)
    public ProblemDetail handlePdfParsing(PdfParsingException ex) {
        log.error("PDF parsing failure: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create("urn:tailor:error:pdf-parsing"));
        return pd;
    }

    @ExceptionHandler(BrainUnavailableException.class)
    public ProblemDetail handleBrainUnavailable(BrainUnavailableException ex) {
        log.error("Brain service unavailable: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The inference engine is currently unavailable. Please try again shortly.");
        pd.setType(URI.create("urn:tailor:error:brain-unavailable"));
        return pd;
    }

    // ------------------------------------------------------------------
    // Validation exceptions
    // ------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setType(URI.create("urn:tailor:error:validation"));
        return pd;
    }

    // ------------------------------------------------------------------
    // Fallback
    // ------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.");
        pd.setType(URI.create("urn:tailor:error:internal"));
        return pd;
    }
}
