package com.tailor.interfaceservice.controller;

import com.tailor.interfaceservice.dto.ChatMessageRequest;
import com.tailor.interfaceservice.service.TailorSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * Follow-up chat endpoint.
 *
 * <p>Accepts a freeform instruction referencing an existing session
 * (e.g. "Make the Java section shorter") and streams the Brain's
 * incremental response back to the caller.
 *
 * <table>
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>POST</td><td>/api/v1/chat</td><td>Follow-up message on an existing session</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final TailorSessionService tailorSessionService;

    /**
     * Send a follow-up message on an existing session and stream the Brain's reply.
     *
     * @param request   contains {@code sessionId} and the user's message
     * @param principal the authenticated user (ownership is enforced at the service layer)
     * @return SSE token stream
     */
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<String> chat(
            @RequestBody @Valid ChatMessageRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        log.info("Chat follow-up: user={}, sessionId={}", principal.getUsername(), request.sessionId());

        return tailorSessionService.chat(
                principal.getUsername(),
                request.sessionId(),
                request.message()
        );
    }
}
