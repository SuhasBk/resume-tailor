package com.tailor.interfaceservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents one complete tailoring session for a user.
 *
 * <p>A session is created when a resume PDF and a job description are first submitted.
 * Follow-up chat messages are stored as child {@link ChatMessage} records, enabling
 * the "Context Management" feature described in the architecture document.
 */
@Entity
@Table(
        name = "tailor_sessions",
        indexes = {
                @Index(name = "idx_sessions_user_id", columnList = "user_id"),
                @Index(name = "idx_sessions_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TailorSession {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "session_seq")
    @SequenceGenerator(name = "session_seq", sequenceName = "tailor_session_seq", allocationSize = 50)
    private Long id;

    /** External identifier for the user (e.g. subject claim from a JWT). */
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    /** Raw text extracted from the uploaded PDF — stored so the Brain always gets clean input. */
    @Column(name = "resume_text", nullable = false, columnDefinition = "TEXT")
    private String resumeText;

    /** Full job description provided by the user. */
    @Column(name = "job_description", nullable = false, columnDefinition = "TEXT")
    private String jobDescription;

    /**
     * The final LaTeX output produced by the Brain for the <em>initial</em> tailoring call.
     * Follow-up outputs are captured in {@link ChatMessage#getBrainResponse()}.
     */
    @Column(name = "latex_output", columnDefinition = "TEXT")
    private String latexOutput;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(
            mappedBy = "session",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<ChatMessage> chatMessages = new ArrayList<>();

    // -------  helpers  -------

    public void addChatMessage(ChatMessage message) {
        chatMessages.add(message);
        message.setSession(this);
    }
}
