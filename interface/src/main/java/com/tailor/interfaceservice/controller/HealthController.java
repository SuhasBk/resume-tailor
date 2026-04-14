package com.tailor.interfaceservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight liveness probe for Docker / Kubernetes health checks.
 * This is separate from the Spring Actuator health endpoint — it is
 * intentionally minimal and always returns 200 if the JVM is alive.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "service", "interface-service",
                "status", "UP"
        ));
    }
}
