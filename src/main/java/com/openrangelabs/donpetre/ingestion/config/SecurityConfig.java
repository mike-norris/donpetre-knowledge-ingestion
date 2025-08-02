package com.openrangelabs.donpetre.ingestion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security configuration for ingestion service
 * Updated for Spring Boot 3.5.3 compatibility
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
                        .pathMatchers("/api/jobs/**").hasAnyRole("ADMIN", "USER")
                        .pathMatchers("/api/credentials/**").hasRole("ADMIN")
                        .pathMatchers("/actuator/**").hasRole("ADMIN")

                        // Default - require authentication
                        .anyExchange().authenticated()
                )
                // FIXED: Proper lambda expression for OAuth2 resource server
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwtSpec -> {
                            // JWT configuration can be customized here if needed
                            // For now, using default JWT decoder configuration
                        })
                )
                .build();
    }

    // Optional: Add custom JWT decoder configuration if needed
    /*
    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        // Custom JWT decoder configuration
        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder
            .withJwkSetUri("https://your-auth-server/.well-known/jwks.json")
            .build();

        // Add custom validation if needed
        jwtDecoder.setJwtValidator(jwtValidator());

        return jwtDecoder;
    }

    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Custom authority mapping logic
            return jwt.getClaimAsStringList("roles").stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toList());
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
    */
}