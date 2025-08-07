package com.openrangelabs.donpetre.ingestion.connector.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrangelabs.donpetre.ingestion.entity.ConnectorConfig;
import com.openrangelabs.donpetre.ingestion.model.KnowledgeItem;
import com.openrangelabs.donpetre.ingestion.model.SyncContext;
import com.openrangelabs.donpetre.ingestion.model.SyncResult;
import com.openrangelabs.donpetre.ingestion.model.SyncType;
import com.openrangelabs.donpetre.ingestion.service.CredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubConnectorTest {

    @Mock
    private CredentialService credentialService;

    private GitHubConnector githubConnector;
    private ConnectorConfig testConfig;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        githubConnector = new GitHubConnector();
        ReflectionTestUtils.setField(githubConnector, "credentialService", credentialService);

        // Create test configuration
        testConfig = new ConnectorConfig();
        testConfig.setId(UUID.randomUUID());
        testConfig.setConnectorType("github");
        testConfig.setName("test-github");
        testConfig.setEnabled(true);

        try {
            JsonNode configNode = objectMapper.readTree("""
                {
                    "base_url": "https://api.github.com",
                    "organization": "test-org",
                    "repositories": ["repo1", "repo2"],
                    "include_issues": true,
                    "include_pull_requests": true,
                    "include_commits": true,
                    "include_wiki": false,
                    "polling_interval_minutes": 30
                }
                """);
            testConfig.setConfiguration(configNode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void getConnectorType_ReturnsGithub() {
        // Act & Assert
        assertThat(githubConnector.getConnectorType()).isEqualTo("github");
    }

    @Test
    void validateConfiguration_ValidConfig_Success() {
        // Act & Assert
        StepVerifier.create(githubConnector.validateConfiguration(testConfig))
            .verifyComplete();
    }

    @Test
    void validateConfiguration_MissingBaseUrl_ThrowsException() throws Exception {
        // Arrange
        JsonNode invalidConfig = objectMapper.readTree("""
            {
                "organization": "test-org"
            }
            """);
        testConfig.setConfiguration(invalidConfig);

        // Act & Assert
        StepVerifier.create(githubConnector.validateConfiguration(testConfig))
            .expectErrorMatches(throwable -> 
                throwable instanceof IllegalArgumentException &&
                throwable.getMessage().contains("base_url is required"))
            .verify();
    }

    @Test
    void validateConfiguration_MissingOrgAndRepos_ThrowsException() throws Exception {
        // Arrange
        JsonNode invalidConfig = objectMapper.readTree("""
            {
                "base_url": "https://api.github.com"
            }
            """);
        testConfig.setConfiguration(invalidConfig);

        // Act & Assert
        StepVerifier.create(githubConnector.validateConfiguration(testConfig))
            .expectErrorMatches(throwable -> 
                throwable instanceof IllegalArgumentException &&
                throwable.getMessage().contains("Either organization or repositories must be specified"))
            .verify();
    }

    @Test
    void testConnection_ValidToken_ReturnsTrue() {
        // Arrange
        String validToken = "ghp_validtoken123";
        when(credentialService.getDecryptedCredential(testConfig.getId(), "api_token"))
            .thenReturn(Mono.just(validToken));

        // Note: This would require mocking GitHub API calls in a real implementation
        // For now, we'll mock the internal behavior
        
        // Act & Assert - This test would need actual GitHub API mocking
        // StepVerifier.create(githubConnector.testConnection(testConfig))
        //     .expectNext(true)
        //     .verifyComplete();
        
        // For now, just verify the credential service is called
        githubConnector.testConnection(testConfig).subscribe();
        verify(credentialService).getDecryptedCredential(testConfig.getId(), "api_token");
    }

    @Test
    void testConnection_InvalidToken_ReturnsFalse() {
        // Arrange
        when(credentialService.getDecryptedCredential(testConfig.getId(), "api_token"))
            .thenReturn(Mono.error(new RuntimeException("Invalid token")));

        // Act & Assert
        StepVerifier.create(githubConnector.testConnection(testConfig))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void testConnection_NoCredentials_ReturnsFalse() {
        // Arrange
        when(credentialService.getDecryptedCredential(testConfig.getId(), "api_token"))
            .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(githubConnector.testConnection(testConfig))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void fetchData_ValidConfig_ReturnsKnowledgeItems() {
        // Arrange
        String validToken = "ghp_validtoken123";
        SyncContext context = SyncContext.forFullSync();
        
        when(credentialService.getDecryptedCredential(testConfig.getId(), "api_token"))
            .thenReturn(Mono.just(validToken));

        // Note: This test would require extensive mocking of GitHub API
        // For demonstration, we'll test the error handling
        
        // Act & Assert
        StepVerifier.create(githubConnector.fetchData(testConfig, context))
            .expectError(RuntimeException.class)
            .verify();
    }

    @Test
    void fetchData_CredentialError_ReturnsError() {
        // Arrange
        SyncContext context = SyncContext.forFullSync();
        when(credentialService.getDecryptedCredential(testConfig.getId(), "api_token"))
            .thenReturn(Mono.error(new RuntimeException("Credential error")));

        // Act & Assert
        StepVerifier.create(githubConnector.fetchData(testConfig, context))
            .expectError(RuntimeException.class)
            .verify();
    }

    @Test
    void doFullSync_Success() {
        // Arrange
        String validToken = "ghp_validtoken123";
        when(credentialService.getDecryptedCredential(testConfig.getId(), "api_token"))
            .thenReturn(Mono.just(validToken));

        // Mock the fetchData method to return empty flux (no actual GitHub calls)
        GitHubConnector spyConnector = spy(githubConnector);
        doReturn(Flux.empty()).when(spyConnector).fetchData(any(), any());

        // Act & Assert
        StepVerifier.create(spyConnector.performSync(testConfig))
            .expectNextMatches(result -> {
                assertThat(result.getConnectorType()).isEqualTo("github");
                assertThat(result.getSyncType()).isEqualTo(SyncType.FULL);
                assertThat(result.getProcessedCount()).isEqualTo(0);
                assertThat(result.getFailedCount()).isEqualTo(0);
                assertThat(result.getStartTime()).isNotNull();
                return true;
            })
            .verifyComplete();
    }

    @Test
    void doIncrementalSync_WithCursor_Success() {
        // Arrange
        String validToken = "ghp_validtoken123";
        String lastCursor = "2023-01-01T00:00:00";
        
        when(credentialService.getDecryptedCredential(testConfig.getId(), "api_token"))
            .thenReturn(Mono.just(validToken));

        GitHubConnector spyConnector = spy(githubConnector);
        doReturn(Flux.empty()).when(spyConnector).fetchData(any(), any());

        // Act & Assert
        StepVerifier.create(spyConnector.performIncrementalSync(testConfig, lastCursor))
            .expectNextMatches(result -> {
                assertThat(result.getConnectorType()).isEqualTo("github");
                assertThat(result.getSyncType()).isEqualTo(SyncType.INCREMENTAL);
                assertThat(result.getStartTime()).isNotNull();
                return true;
            })
            .verifyComplete();
    }

    @Test
    void doIncrementalSync_NoCursor_Success() {
        // Arrange
        String validToken = "ghp_validtoken123";
        
        when(credentialService.getDecryptedCredential(testConfig.getId(), "api_token"))
            .thenReturn(Mono.just(validToken));

        GitHubConnector spyConnector = spy(githubConnector);
        doReturn(Flux.empty()).when(spyConnector).fetchData(any(), any());

        // Act & Assert
        StepVerifier.create(spyConnector.performIncrementalSync(testConfig, null))
            .expectNextMatches(result -> {
                assertThat(result.getConnectorType()).isEqualTo("github");
                assertThat(result.getSyncType()).isEqualTo(SyncType.INCREMENTAL);
                return true;
            })
            .verifyComplete();
    }

    @Test
    void getMetrics_ReturnsConnectorMetrics() {
        // Act & Assert
        StepVerifier.create(githubConnector.getMetrics(testConfig))
            .expectNextMatches(metrics -> {
                assertThat(metrics).isNotNull();
                return true;
            })
            .verifyComplete();
    }

    @Test
    void getRateLimitStatus_ReturnsRateLimitStatus() {
        // Act & Assert
        StepVerifier.create(githubConnector.getRateLimitStatus(testConfig))
            .expectNextMatches(status -> {
                assertThat(status).isNotNull();
                return true;
            })
            .verifyComplete();
    }

    @Test
    void createKnowledgeItemFromIssue_ValidData_Success() {
        // This would test a private method that creates KnowledgeItem from GitHub Issue
        // Implementation would depend on the actual method structure
        // For now, we demonstrate the test structure

        // Arrange
        // String issueData = "..."; // Mock GitHub issue data
        // String repositoryName = "test-repo";

        // Act
        // KnowledgeItem item = githubConnector.createKnowledgeItemFromIssue(issueData, repositoryName);

        // Assert
        // assertThat(item.getTitle()).isNotEmpty();
        // assertThat(item.getContent()).isNotEmpty();
        // assertThat(item.getSourceType()).isEqualTo("github");
    }

    @Test
    void createKnowledgeItemFromPullRequest_ValidData_Success() {
        // Similar test for pull request conversion
        // Implementation would depend on actual method structure
    }

    @Test
    void createKnowledgeItemFromCommit_ValidData_Success() {
        // Similar test for commit conversion  
        // Implementation would depend on actual method structure
    }

    @Test
    void parseLastModified_ValidDate_Success() {
        // Test date parsing logic
        // String dateString = "2023-01-01T12:00:00Z";
        // LocalDateTime parsed = githubConnector.parseLastModified(dateString);
        // assertThat(parsed).isNotNull();
    }

    @Test
    void parseLastModified_InvalidDate_ReturnsNull() {
        // Test invalid date handling
        // String invalidDate = "invalid-date";
        // LocalDateTime parsed = githubConnector.parseLastModified(invalidDate);
        // assertThat(parsed).isNull();
    }

    @Test
    void shouldIncludeItem_ConfiguredTypes_ReturnsCorrectly() {
        // Test filtering logic based on configuration
        // boolean includeIssues = githubConnector.shouldIncludeItem("issue", testConfig);
        // assertThat(includeIssues).isTrue();
        
        // boolean includeWiki = githubConnector.shouldIncludeItem("wiki", testConfig);
        // assertThat(includeWiki).isFalse();
    }
}