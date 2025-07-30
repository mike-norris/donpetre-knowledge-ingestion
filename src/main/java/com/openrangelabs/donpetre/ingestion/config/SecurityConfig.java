package com.openrangelabs.donpetre.ingestion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security configuration for ingestion service
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(exchanges -> exchanges
                        // Public endpoints
                        .pathMatchers(HttpMethod.GET, "/actuator/health", "/health").permitAll()
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Protected endpoints
                        .pathMatchers("/api/connectors/**").hasAnyRole("ADMIN", "USER")
                        .pathMatchers("/api/ingestion/**").hasAnyRole("ADMIN", "USER")
                        .pathMatchers("/actuator/**").hasRole("ADMIN")

                        // Default - require authentication
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt())
                .build();
    }
}