package com.tailor.interfaceservice.service;

import com.tailor.interfaceservice.exception.PdfParsingException;
import com.tailor.interfaceservice.metrics.TailorMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Ingestion and transformation layer.
 *
 * <p>Accepts a raw PDF {@link MultipartFile}, delegates text extraction to
 * Apache PDFBox, and sanitises the result before returning it to callers.
 * Sanitisation removes non-printable control characters (except whitespace)
 * that would otherwise pollute the LLM prompt and waste tokens.
 *
 * <p>Parse latency is recorded via {@link TailorMetrics} and exposed on
 * the Prometheus endpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfParsingService {

    private static final String NON_PRINTABLE_REGEX = "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]";

    private final TailorMetrics metrics;

    /**
     * Extract and sanitise text from a PDF upload.
     *
     * @param file the uploaded PDF file
     * @return sanitised plain text ready for LLM consumption
     * @throws PdfParsingException if the file cannot be opened or read by PDFBox
     */
    public String extractText(MultipartFile file) {
        log.debug("Starting PDF extraction for file: {}, size: {} bytes",
                file.getOriginalFilename(), file.getSize());

        long start = System.currentTimeMillis();

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);           // preserve visual reading order
            String rawText = stripper.getText(document);

            String sanitised = sanitise(rawText);
            long elapsed = System.currentTimeMillis() - start;
            metrics.recordPdfParseTime(elapsed);

            log.info("PDF extraction complete: {} pages, {} chars, {}ms",
                    document.getNumberOfPages(), sanitised.length(), elapsed);

            return sanitised;

        } catch (IOException e) {
            throw new PdfParsingException(
                    "Failed to parse PDF file: " + file.getOriginalFilename(), e);
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Strip non-printable ASCII control characters while preserving all
     * legitimate whitespace (space, tab, newline, carriage return).
     */
    private String sanitise(String raw) {
        return raw.replaceAll(NON_PRINTABLE_REGEX, "")
                  .replaceAll("[ \\t]+", " ")       // collapse horizontal whitespace
                  .replaceAll("(\\r?\\n){3,}", "\n\n") // cap consecutive blank lines at 2
                  .trim();
    }
}
