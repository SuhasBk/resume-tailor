package com.tailor.interfaceservice.controller;

import com.tailor.interfaceservice.dto.SessionSummaryResponse;
import com.tailor.interfaceservice.entity.TailorSession;
import com.tailor.interfaceservice.service.TailorSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Session history and detail endpoints.
 *
 * <table>
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/v1/sessions</td><td>List all sessions for the current user</td></tr>
 *   <tr><td>GET</td><td>/api/v1/sessions/{id}</td><td>Full detail for one session (resume, JD, LaTeX)</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Slf4j
public class SessionController {

    private final TailorSessionService tailorSessionService;

    /**
     * List all sessions owned by the authenticated user, newest first.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<SessionSummaryResponse>> listSessions(
            @AuthenticationPrincipal UserDetails principal) {

        log.debug("List sessions for user={}", principal.getUsername());
        return ResponseEntity.ok(tailorSessionService.getSessionsForUser(principal.getUsername()));
    }

    /**
     * Retrieve full session detail including the original resume text,
     * job description, initial LaTeX output, and all chat messages.
     *
     * @param id the session identifier
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TailorSession> getSession(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {

        log.debug("Get session id={} for user={}", id, principal.getUsername());
        return ResponseEntity.ok(tailorSessionService.getSession(principal.getUsername(), id));
    }
}
