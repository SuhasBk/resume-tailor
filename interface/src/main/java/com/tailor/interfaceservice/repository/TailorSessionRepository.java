package com.tailor.interfaceservice.repository;

import com.tailor.interfaceservice.entity.TailorSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TailorSessionRepository extends JpaRepository<TailorSession, Long> {

    /** All sessions for a given user, newest first — used for the history endpoint. */
    List<TailorSession> findByUserIdOrderByCreatedAtDesc(String userId);

    /** Strict ownership check: fetch a session only if it belongs to the given user. */
    Optional<TailorSession> findByIdAndUserId(Long id, String userId);
}
