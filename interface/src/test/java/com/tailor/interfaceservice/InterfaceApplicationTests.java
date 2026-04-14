package com.tailor.interfaceservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test — verifies the Spring application context loads successfully
 * with an in-memory H2 datasource so no real PostgreSQL or Brain service
 * is required during CI.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "brain.service.base-url=http://localhost:9999",
        "brain.service.tailor-path=/tailor"
})
class InterfaceApplicationTests {

    @Test
    void contextLoads() {
        // Context load is the assertion
    }
}
