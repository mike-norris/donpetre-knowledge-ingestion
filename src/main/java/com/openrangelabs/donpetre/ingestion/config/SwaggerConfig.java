package com.openrangelabs.donpetre.ingestion.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for OpenAPI 3 / Swagger documentation.
 *
 * <p>Configures comprehensive API documentation including security schemes,
 * server information, and detailed API metadata for the knowledge ingestion service.
 *
 * @author OpenRange Labs
 * @version 1.0
 * @since 2025-01
 */
@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${spring.application.name:knowledge-ingestion}")
    private String applicationName;

    /**
     * Configures the OpenAPI specification for the knowledge ingestion service.
     *
     * @return configured OpenAPI instance
     */
    @Bean
    public OpenAPI knowledgeIngestionOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(serverList())
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(securityComponents());
    }

    private Info apiInfo() {
        return new Info()
                .title("DonPetre Knowledge Ingestion API")
                .description("""
                # DonPetre Knowledge Ingestion Service
                
                This service provides secure credential management and data ingestion capabilities 
                for the DonPetre Knowledge Platform.
                
                ## Key Features
                
                * **Secure Credential Storage**: AES-256 encrypted credential storage
                * **Credential Lifecycle Management**: Rotation, expiration monitoring, and deactivation
                * **Multi-Source Integration**: GitHub, GitLab, Jira, Slack, and more
                * **Reactive Architecture**: Built on Spring WebFlux for high performance
                * **Comprehensive Monitoring**: Health checks, metrics, and usage analytics
                
                ## Security
                
                All endpoints require authentication with Admin role privileges. Credential values
                are never returned in API responses for security reasons.
                
                ## Rate Limiting
                
                API requests are subject to rate limiting to ensure fair usage and system stability.
                
                ## Support
                
                For technical support, please contact the development team.
                """)
                .version("1.0.0")
                .contact(new Contact()
                        .name("OpenRange Labs Development Team")
                        .email("dev@openrangelabs.com")
                        .url("https://openrangelabs.com"))
                .license(new License()
                        .name("Proprietary")
                        .url("https://openrangelabs.com/license"));
    }

    private List<Server> serverList() {
        return List.of(
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Local development server"),
                new Server()
                        .url("https://api-dev.donpetre.com")
                        .description("Development environment"),
                new Server()
                        .url("https://api-staging.donpetre.com")
                        .description("Staging environment"),
                new Server()
                        .url("https://api.donpetre.com")
                        .description("Production environment")
        );
    }

    private Components securityComponents() {
        return new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT token obtained from authentication service"));
    }
}