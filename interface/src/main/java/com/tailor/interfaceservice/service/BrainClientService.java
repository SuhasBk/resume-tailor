package com.tailor.interfaceservice.service;

import com.tailor.interfaceservice.dto.BrainRequest;
import com.tailor.interfaceservice.exception.BrainUnavailableException;
import com.tailor.interfaceservice.metrics.TailorMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

/**
 * Reactive Orchestration layer — the "Director-to-Brain" channel.
 *
 * <p>Uses a non-blocking {@link WebClient} to call {@code POST /tailor} on the
 * Python Brain and returns a {@link Flux} of raw SSE token strings. The Flux
 * is handed directly to the HTTP response writer so tokens reach the browser
 * as they are produced by Groq — there is no internal buffering step.
 *
 * <p>Error handling:
 * <ul>
 *   <li>Network-level failures ({@link WebClientRequestException}) → {@link BrainUnavailableException}</li>
 *   <li>5xx responses from the Brain → {@link BrainUnavailableException}</li>
 *   <li>4xx responses (malformed request) → re-thrown as-is for upstream handling</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrainClientService {

    private final WebClient brainWebClient;
    private final TailorMetrics metrics;

    /**
     * Stream tokens from the Brain for the given resume + job description pair.
     *
     * @param resumeText      sanitised plain-text resume
     * @param jobDescription  the target job description
     * @return a cold {@link Flux} of token strings; subscribe to begin the stream
     */
    public Flux<String> streamTailoredResume(String resumeText, String jobDescription) {
        log.debug("Opening Brain stream for resume length={}, jd length={}",
                resumeText.length(), jobDescription.length());

        return brainWebClient.post()
                .bodyValue(new BrainRequest(resumeText, jobDescription))
                .retrieve()
                .bodyToFlux(String.class)
                .doOnError(WebClientResponseException.class, ex -> {
                    log.error("Brain returned HTTP {}: {}", ex.getStatusCode(), ex.getMessage());
                    metrics.incrementBrainStreamErrors();
                })
                .doOnError(WebClientRequestException.class, ex -> {
                    log.error("Cannot reach Brain service: {}", ex.getMessage());
                    metrics.incrementBrainStreamErrors();
                })
                .onErrorMap(WebClientRequestException.class, ex ->
                        new BrainUnavailableException("Brain service is unreachable", ex))
                .onErrorMap(WebClientResponseException.class, ex -> {
                    if (ex.getStatusCode().is5xxServerError()) {
                        return new BrainUnavailableException("Brain returned a server error", ex);
                    }
                    return ex; // propagate 4xx unchanged
                });
    }
}
