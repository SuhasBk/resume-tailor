package com.tailor.interfaceservice.repository;

import com.tailor.interfaceservice.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** All messages belonging to a session, ordered chronologically. */
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
