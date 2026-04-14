package com.tailor.interfaceservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration with two profiles:
 *
 * <ul>
 *   <li><b>default / local</b> — HTTP Basic auth backed by an in-memory user.
 *       CSRF is disabled so {@code curl} and Postman work without friction.
 *       Actuator endpoints are publicly accessible for local Prometheus scraping.</li>
 *   <li><b>prod</b> — The {@code localSecurityFilterChain} bean is NOT loaded;
 *       a separate OAuth2/JWT filter chain (not shown here) takes over, enforcing
 *       bearer token validation and strict data isolation per the architecture doc.</li>
 * </ul>
 *
 * <p>All API routes under {@code /api/**} require authentication in both profiles.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // ------------------------------------------------------------------
    // Local / dev profile: in-memory user + HTTP Basic
    // ------------------------------------------------------------------

    /**
     * Simple filter chain active on all profiles except {@code prod}.
     * Allows local development without a real identity provider.
     */
    @Bean
    @Profile("!prod")
    public SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Actuator endpoints are open for Prometheus scraping
                        .requestMatchers("/actuator/**").permitAll()
                        // Health check
                        .requestMatchers("/api/v1/health").permitAll()
                        // Everything else requires an authenticated principal
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> {});   // simple Basic auth for local curl/Postman testing

        return http.build();
    }

    /**
     * In-memory user store for local development.
     * Credentials: {@code dev / dev-secret} — never deployed to production.
     */
    @Bean
    @Profile("!prod")
    public UserDetailsService localUserDetailsService(PasswordEncoder encoder) {
        var user = User.builder()
                .username("dev")
                .password(encoder.encode("dev-secret"))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
