package com.openrangelabs.donpetre.ingestion.service;

import com.openrangelabs.donpetre.ingestion.dto.CredentialResponseDto;
import com.openrangelabs.donpetre.ingestion.dto.StoreCredentialRequest;
import com.openrangelabs.donpetre.ingestion.entity.ApiCredential;
import com.openrangelabs.donpetre.ingestion.exception.CredentialAlreadyExistsException;
import com.openrangelabs.donpetre.ingestion.exception.CredentialEncryptionException;
import com.openrangelabs.donpetre.ingestion.exception.CredentialNotFoundException;
import com.openrangelabs.donpetre.ingestion.repository.ApiCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CredentialServiceTest {

    @Mock
    private ApiCredentialRepository repository;

    private CredentialService credentialService;
    private String testEncryptionKey;

    @BeforeEach
    void setUp() throws Exception {
        // Generate a test encryption key
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        SecretKey secretKey = keyGenerator.generateKey();
        testEncryptionKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());

        credentialService = new CredentialService(repository, testEncryptionKey);
    }

    @Test
    void storeCredential_Success() {
        // Arrange
        UUID connectorId = UUID.randomUUID();
        String credentialName = "api_token";
        String credentialValue = "secret-token-123";

        StoreCredentialRequest request = new StoreCredentialRequest();
        request.setConnectorConfigId(connectorId);
        request.setCredentialType(credentialName);
        request.setValue(credentialValue);

        ApiCredential savedCredential = new ApiCredential();
        savedCredential.setId(UUID.randomUUID());
        savedCredential.setConnectorConfigId(connectorId);
        savedCredential.setCredentialType(credentialName);
        savedCredential.setEncryptedValue("encrypted-value");
        savedCredential.setCreatedAt(LocalDateTime.now());

        when(repository.existsByConnectorIdAndCredentialName(connectorId, credentialName))
            .thenReturn(Mono.just(false));
        when(repository.save(any(ApiCredential.class)))
            .thenReturn(Mono.just(savedCredential));

        // Act & Assert
        StepVerifier.create(credentialService.storeCredential(request))
            .expectNextMatches(response -> {
                assertThat(response.getId()).isEqualTo(savedCredential.getId());
                assertThat(response.getConnectorConfigId()).isEqualTo(connectorId);
                assertThat(response.getCredentialType()).isEqualTo(credentialName);
                assertThat(response.getCreatedAt()).isEqualTo(savedCredential.getCreatedAt());
                // Credential value is not included in response for security
                return true;
            })
            .verifyComplete();

        verify(repository).save(argThat(credential -> 
            credential.getConnectorConfigId().equals(connectorId) &&
            credential.getCredentialType().equals(credentialName) &&
            credential.getEncryptedValue() != null &&
            !credential.getEncryptedValue().equals(credentialValue) // Should be encrypted
        ));
    }

    @Test
    void storeCredential_AlreadyExists_ThrowsException() {
        // Arrange
        UUID connectorId = UUID.randomUUID();
        String credentialName = "api_token";

        StoreCredentialRequest request = new StoreCredentialRequest();
        request.setConnectorConfigId(connectorId);
        request.setCredentialType(credentialName);
        request.setValue("secret-token-123");

        when(repository.existsByConnectorIdAndCredentialName(connectorId, credentialName))
            .thenReturn(Mono.just(true));

        // Act & Assert
        StepVerifier.create(credentialService.storeCredential(request))
            .expectError(CredentialAlreadyExistsException.class)
            .verify();

        verify(repository, never()).save(any());
    }

    @Test
    void getCredential_Found() {
        // Arrange
        UUID credentialId = UUID.randomUUID();
        ApiCredential credential = new ApiCredential();
        credential.setId(credentialId);
        credential.setConnectorConfigId(UUID.randomUUID());
        credential.setCredentialType("api_token");
        credential.setEncryptedValue("encrypted-value");
        credential.setCreatedAt(LocalDateTime.now());

        when(repository.findById(credentialId))
            .thenReturn(Mono.just(credential));

        // Act & Assert
        StepVerifier.create(credentialService.getCredential(credentialId))
            .expectNextMatches(response -> {
                assertThat(response.getId()).isEqualTo(credentialId);
                assertThat(response.getCredentialType()).isEqualTo("api_token");
                // Credential value is not included in response for security
                return true;
            })
            .verifyComplete();
    }

    @Test
    void getCredential_NotFound() {
        // Arrange
        UUID credentialId = UUID.randomUUID();
        when(repository.findById(credentialId))
            .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(credentialService.getCredential(credentialId))
            .verifyComplete();
    }

    @Test
    void getCredentialsByConnector() {
        // Arrange
        UUID connectorId = UUID.randomUUID();
        ApiCredential credential1 = new ApiCredential();
        credential1.setId(UUID.randomUUID());
        credential1.setConnectorConfigId(connectorId);
        credential1.setCredentialType("api_token");

        ApiCredential credential2 = new ApiCredential();
        credential2.setId(UUID.randomUUID());
        credential2.setConnectorConfigId(connectorId);
        credential2.setCredentialType("webhook_secret");

        when(repository.findByConnectorConfigId(connectorId))
            .thenReturn(Flux.just(credential1, credential2));

        // Act & Assert
        StepVerifier.create(credentialService.getCredentialsByConnector(connectorId))
            .expectNextCount(2)
            .verifyComplete();
    }

    @Test
    void getDecryptedCredential_Success() throws Exception {
        // Arrange
        UUID connectorId = UUID.randomUUID();
        String credentialName = "api_token";
        String originalValue = "secret-token-123";

        // Manually encrypt the value for testing
        String encryptedValue = encryptValue(originalValue);

        ApiCredential credential = new ApiCredential();
        credential.setConnectorConfigId(connectorId);
        credential.setCredentialType(credentialName);
        credential.setEncryptedValue(encryptedValue);

        when(repository.findActiveByConnectorConfigIdAndCredentialType(connectorId, credentialName))
            .thenReturn(Mono.just(credential));
        when(repository.updateLastUsed(any(UUID.class)))
            .thenReturn(Mono.just(1));

        // Act & Assert
        StepVerifier.create(credentialService.getDecryptedCredential(connectorId, credentialName))
            .expectNext(originalValue)
            .verifyComplete();
    }

    @Test
    void getDecryptedCredential_NotFound_ReturnsEmpty() {
        // Arrange
        UUID connectorId = UUID.randomUUID();
        String credentialName = "nonexistent";

        when(repository.findActiveByConnectorConfigIdAndCredentialType(connectorId, credentialName))
            .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(credentialService.getDecryptedCredential(connectorId, credentialName))
            .verifyComplete();
    }

    @Test
    void updateCredential_Success() {
        // Arrange
        UUID credentialId = UUID.randomUUID();
        String newValue = "new-secret-token";

        ApiCredential existingCredential = new ApiCredential();
        existingCredential.setId(credentialId);
        existingCredential.setConnectorConfigId(UUID.randomUUID());
        existingCredential.setCredentialType("api_token");
        existingCredential.setEncryptedValue("old-encrypted-value");

        ApiCredential updatedCredential = new ApiCredential();
        updatedCredential.setId(credentialId);
        updatedCredential.setConnectorConfigId(existingCredential.getConnectorConfigId());
        updatedCredential.setCredentialType("api_token");
        updatedCredential.setEncryptedValue("new-encrypted-value");

        when(repository.findById(credentialId))
            .thenReturn(Mono.just(existingCredential));
        when(repository.save(any(ApiCredential.class)))
            .thenReturn(Mono.just(updatedCredential));

        // Act & Assert
        StepVerifier.create(credentialService.updateCredential(credentialId, newValue))
            .expectNextMatches(response -> {
                assertThat(response.getId()).isEqualTo(credentialId);
                assertThat(response.getCredentialType()).isEqualTo("api_token");
                return true;
            })
            .verifyComplete();

        verify(repository).save(argThat(credential -> 
            !credential.getEncryptedValue().equals("old-encrypted-value")
        ));
    }

    @Test
    void updateCredential_NotFound() {
        // Arrange
        UUID credentialId = UUID.randomUUID();
        when(repository.findById(credentialId))
            .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(credentialService.updateCredential(credentialId, "new-value"))
            .expectError(CredentialNotFoundException.class)
            .verify();

        verify(repository, never()).save(any());
    }

    @Test
    void deleteCredential_Success() {
        // Arrange
        UUID credentialId = UUID.randomUUID();
        when(repository.deleteById(credentialId))
            .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(credentialService.deleteCredential(credentialId))
            .verifyComplete();

        verify(repository).deleteById(credentialId);
    }

    @Test
    void deleteCredentialsByConnector_Success() {
        // Arrange
        UUID connectorId = UUID.randomUUID();
        when(repository.deleteByConnectorId(connectorId))
            .thenReturn(Mono.just(3L));

        // Act & Assert
        StepVerifier.create(credentialService.deleteCredentialsByConnector(connectorId))
            .expectNext(3L)
            .verifyComplete();

        verify(repository).deleteByConnectorId(connectorId);
    }

    @Test
    void getCredentialUsage() {
        // Arrange
        UUID connectorId = UUID.randomUUID();
        // Create mock CredentialUsageResult instances
        ApiCredentialRepository.CredentialUsageResult result1 = new ApiCredentialRepository.CredentialUsageResult() {
            @Override
            public String getCredentialName() { return "api_token"; }
            @Override
            public LocalDateTime getLastUsed() { return LocalDateTime.now(); }
        };
        ApiCredentialRepository.CredentialUsageResult result2 = new ApiCredentialRepository.CredentialUsageResult() {
            @Override
            public String getCredentialName() { return "webhook_secret"; }
            @Override
            public LocalDateTime getLastUsed() { return LocalDateTime.now().minusDays(1); }
        };
        
        when(repository.findCredentialUsage(connectorId))
            .thenReturn(Flux.just(result1, result2));

        // Act & Assert
        StepVerifier.create(credentialService.getCredentialUsage(connectorId))
            .expectNextCount(2)
            .verifyComplete();
    }

    @Test
    void rotateCredential_Success() {
        // Arrange
        UUID credentialId = UUID.randomUUID();
        String newValue = "rotated-credential";

        ApiCredential existingCredential = new ApiCredential();
        existingCredential.setId(credentialId);
        existingCredential.setEncryptedValue("old-value");

        ApiCredential rotatedCredential = new ApiCredential();
        rotatedCredential.setId(credentialId);
        rotatedCredential.setEncryptedValue("new-encrypted-value");
        // Note: ApiCredential does not have updatedAt field

        when(repository.findById(credentialId))
            .thenReturn(Mono.just(existingCredential));
        when(repository.save(any(ApiCredential.class)))
            .thenReturn(Mono.just(rotatedCredential));

        // Act & Assert
        StepVerifier.create(credentialService.rotateCredential(credentialId, newValue))
            .expectNext(rotatedCredential)
            .verifyComplete();

        verify(repository).save(any(ApiCredential.class));
    }

    @Test
    void testCredential_Success() throws Exception {
        // Arrange
        UUID connectorId = UUID.randomUUID();
        String credentialName = "api_token";
        String credentialValue = "test-token";

        String encryptedValue = encryptValue(credentialValue);
        
        ApiCredential credential = new ApiCredential();
        credential.setEncryptedValue(encryptedValue);

        when(repository.findByConnectorIdAndCredentialName(connectorId, credentialName))
            .thenReturn(Mono.just(credential));

        // Act & Assert
        StepVerifier.create(credentialService.testCredential(connectorId, credentialName))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void testCredential_NotFound() {
        // Arrange
        UUID connectorId = UUID.randomUUID();
        String credentialName = "nonexistent";

        when(repository.findByConnectorIdAndCredentialName(connectorId, credentialName))
            .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(credentialService.testCredential(connectorId, credentialName))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void testCredential_DecryptionError() {
        // Arrange
        UUID connectorId = UUID.randomUUID();
        String credentialName = "api_token";

        ApiCredential credential = new ApiCredential();
        credential.setEncryptedValue("invalid-encrypted-value");

        when(repository.findByConnectorIdAndCredentialName(connectorId, credentialName))
            .thenReturn(Mono.just(credential));

        // Act & Assert
        StepVerifier.create(credentialService.testCredential(connectorId, credentialName))
            .expectNext(false)
            .verifyComplete();
    }

    // Helper method for testing encryption
    private String encryptValue(String value) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(testEncryptionKey);
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
        
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        
        byte[] encrypted = cipher.doFinal(value.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }
}