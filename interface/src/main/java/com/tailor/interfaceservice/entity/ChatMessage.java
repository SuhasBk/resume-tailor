package com.tailor.interfaceservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A single follow-up exchange within a {@link TailorSession}.
 *
 * <p>Each row represents one user turn: the user's freeform instruction
 * (e.g. "Make the Java section shorter") and the Brain's response.
 * This table forms the conversational history that the service appends
 * to the LLM context on subsequent calls.
 */
@Entity
@Table(
        name = "chat_messages",
        indexes = {
                @Index(name = "idx_chat_session_id", columnList = "session_id"),
                @Index(name = "idx_chat_created_at",  columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chat_msg_seq")
    @SequenceGenerator(name = "chat_msg_seq", sequenceName = "chat_message_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private TailorSession session;

    /** The user's follow-up instruction. */
    @Column(name = "user_message", nullable = false, columnDefinition = "TEXT")
    private String userMessage;

    /**
     * The Brain's streaming response, buffered and persisted after the stream completes.
     * {@code null} while the stream is in-flight.
     */
    @Column(name = "brain_response", columnDefinition = "TEXT")
    private String brainResponse;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
