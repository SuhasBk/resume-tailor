package com.tailor.interfaceservice.service;

import com.tailor.interfaceservice.dto.SessionSummaryResponse;
import com.tailor.interfaceservice.entity.ChatMessage;
import com.tailor.interfaceservice.entity.TailorSession;
import com.tailor.interfaceservice.exception.SessionNotFoundException;
import com.tailor.interfaceservice.metrics.TailorMetrics;
import com.tailor.interfaceservice.repository.ChatMessageRepository;
import com.tailor.interfaceservice.repository.TailorSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Persistence and Context Management layer.
 *
 * <p>Owns the full lifecycle of a {@link TailorSession}:
 * <ol>
 *   <li>Creates a new session when a user first submits a resume + JD.</li>
 *   <li>Persists the Brain's initial LaTeX output once the stream completes.</li>
 *   <li>Appends follow-up chat turns, hydrating the full context so the Brain
 *       always receives the accumulated conversation history.</li>
 * </ol>
 *
 * <p>All write operations are wrapped in a transaction. Read operations are
 * annotated with {@code readOnly = true} to allow the JDBC driver to route
 * them to a read replica if one is configured.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TailorSessionService {

    private final TailorSessionRepository sessionRepository;
    private final ChatMessageRepository    chatMessageRepository;
    private final BrainClientService       brainClientService;
    private final TailorMetrics            metrics;

    // ------------------------------------------------------------------
    // Session creation
    // ------------------------------------------------------------------

    /**
     * Persists a new session and begins streaming the Brain's response.
     * The stream is a "cold" Flux; the Brain call does not start until a
     * subscriber (the HTTP response writer) actually subscribes.
     *
     * <p>After the stream completes, the concatenated LaTeX output is saved
     * back to the session row and the resume-processed counter is incremented.
     *
     * @param userId        the authenticated user's identifier
     * @param resumeText    sanitised PDF text
     * @param jobDescription the target job description
     * @return a {@link Flux} of token strings to be streamed to the client
     */
    @Transactional
    public Flux<String> createSessionAndStream(String userId, String resumeText, String jobDescription) {
        TailorSession session = TailorSession.builder()
                .userId(userId)
                .resumeText(resumeText)
                .jobDescription(jobDescription)
                .build();
        TailorSession saved = sessionRepository.save(session);
        log.info("Created session id={} for user={}", saved.getId(), userId);

        StringBuilder buffer = new StringBuilder();

        return brainClientService.streamTailoredResume(resumeText, jobDescription)
                .doOnNext(buffer::append)
                .doOnComplete(() -> {
                    saved.setLatexOutput(buffer.toString());
                    sessionRepository.save(saved);
                    metrics.incrementResumesProcessed();
                    log.info("Session id={} completed, {} chars persisted", saved.getId(), buffer.length());
                })
                .doOnError(ex -> log.error("Session id={} stream error: {}", saved.getId(), ex.getMessage()));
    }

    // ------------------------------------------------------------------
    // Chat follow-up
    // ------------------------------------------------------------------

    /**
     * Handles a follow-up chat message on an existing session.
     *
     * <p>The service reconstructs the full context (original resume + previous
     * LaTeX output) and appends the user's new instruction before sending the
     * enriched prompt to the Brain. The Brain's response is buffered and
     * persisted as a new {@link ChatMessage} row once the stream finishes.
     *
     * @param userId     the authenticated user (ownership guard)
     * @param sessionId  the session to continue
     * @param userMessage the follow-up instruction
     * @return a streaming {@link Flux} of token strings
     */
    @Transactional
    public Flux<String> chat(String userId, Long sessionId, String userMessage) {
        TailorSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        // Build enriched context: prepend previous output to the user's message
        String enrichedJobDescription = buildEnrichedContext(session, userMessage);

        ChatMessage msg = ChatMessage.builder()
                .userMessage(userMessage)
                .build();
        session.addChatMessage(msg);
        sessionRepository.save(session);

        StringBuilder buffer = new StringBuilder();

        return brainClientService.streamTailoredResume(session.getResumeText(), enrichedJobDescription)
                .doOnNext(buffer::append)
                .doOnComplete(() -> {
                    msg.setBrainResponse(buffer.toString());
                    chatMessageRepository.save(msg);
                    log.info("Chat message id={} on session id={} persisted ({} chars)",
                            msg.getId(), sessionId, buffer.length());
                });
    }

    // ------------------------------------------------------------------
    // Read operations
    // ------------------------------------------------------------------

    /**
     * Returns lightweight summaries of all sessions owned by a user,
     * ordered newest-first.
     */
    @Transactional(readOnly = true)
    public List<SessionSummaryResponse> getSessionsForUser(String userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(s -> new SessionSummaryResponse(
                        s.getId(),
                        s.getUserId(),
                        snippet(s.getJobDescription(), 120),
                        s.getCreatedAt(),
                        s.getUpdatedAt()))
                .toList();
    }

    /**
     * Returns the full detail of one session, enforcing ownership.
     */
    @Transactional(readOnly = true)
    public TailorSession getSession(String userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Builds an enriched "job description" string that includes the previously
     * generated LaTeX output so the Brain can apply incremental edits.
     */
    private String buildEnrichedContext(TailorSession session, String userMessage) {
        String previousOutput = session.getLatexOutput() != null
                ? session.getLatexOutput() : "(no previous output)";

        return """
                [PREVIOUS LATEX OUTPUT]
                %s

                [USER FOLLOW-UP INSTRUCTION]
                %s

                [ORIGINAL JOB DESCRIPTION]
                %s
                """.formatted(previousOutput, userMessage, session.getJobDescription());
    }

    private String snippet(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "…";
    }
}
