package com.tailor.interfaceservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Custom business metrics exposed on {@code /actuator/prometheus}.
 *
 * <ul>
 *   <li>{@code tailor_resumes_processed_total} — running count of every successful
 *       PDF-to-Brain pipeline execution.</li>
 *   <li>{@code tailor_pdf_parse_seconds} — histogram of PDF text-extraction latency
 *       (P50 / P95 / P99 surfaced in Grafana).</li>
 *   <li>{@code tailor_brain_stream_errors_total} — count of upstream Brain failures
 *       so alerts can fire without waiting for HTTP error-rate dashboards.</li>
 * </ul>
 */
@Component
public class TailorMetrics {

    private final Counter resumesProcessed;
    private final Timer   pdfParseTimer;
    private final Counter brainStreamErrors;

    public TailorMetrics(MeterRegistry registry) {
        this.resumesProcessed = Counter.builder("tailor_resumes_processed_total")
                .description("Total number of resumes successfully processed end-to-end")
                .register(registry);

        this.pdfParseTimer = Timer.builder("tailor_pdf_parse_seconds")
                .description("Latency of PDF text extraction via PDFBox")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.brainStreamErrors = Counter.builder("tailor_brain_stream_errors_total")
                .description("Total number of Brain streaming failures")
                .register(registry);
    }

    public void incrementResumesProcessed() {
        resumesProcessed.increment();
    }

    public void recordPdfParseTime(long durationMs) {
        pdfParseTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void incrementBrainStreamErrors() {
        brainStreamErrors.increment();
    }
}
