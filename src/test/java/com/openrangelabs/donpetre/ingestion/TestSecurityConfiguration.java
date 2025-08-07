package com.openrangelabs.donpetre.ingestion;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.TestPropertySource;

/**
 * Test configuration for integration tests
 */
@TestConfiguration
@EnableWebFluxSecurity
@TestPropertySource(properties = {
    "spring.r2dbc.url=r2dbc:h2:mem:///testdb",
    "ingestion.encryption.secret-key=dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRlcw==",
    "ingestion.jobs.max-concurrent-jobs=3",
    "ingestion.scheduling.enabled=false"
})
public class TestSecurityConfiguration {

    /**
     * Disable security for tests
     */
    @Bean
    public SecurityWebFilterChain testSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
            .csrf(csrf -> csrf.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(formLogin -> formLogin.disable())
            .build();
    }
}