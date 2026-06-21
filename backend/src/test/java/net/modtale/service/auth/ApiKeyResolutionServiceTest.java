package net.modtale.service.auth;

import java.util.List;
import java.util.Optional;
import net.modtale.exception.ApiKeyOperationForbiddenException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyResolutionServiceTest {

    private ApiKeyRepository apiKeyRepository;
    private UserRepository userRepository;
    private ApiKeyIssuanceService apiKeyIssuanceService;
    private ApiKeyResolutionService service;

    @BeforeEach
    void setUp() {
        apiKeyRepository = mock(ApiKeyRepository.class);
        userRepository = mock(UserRepository.class);
        apiKeyIssuanceService = mock(ApiKeyIssuanceService.class);
        service = new ApiKeyResolutionService(apiKeyRepository, userRepository, apiKeyIssuanceService, Runnable::run);
    }

    @Test
    void resolveKeyRejectsNullShortMissingAndHashMismatchedKeys() {
        ApiKey storedKey = new ApiKey("user-1", "CI", new BCryptPasswordEncoder().encode("md_valid-secret"), "md_valid-s");

        when(apiKeyRepository.findByPrefix("md_missing")).thenReturn(Optional.empty());
        when(apiKeyRepository.findByPrefix("md_valid-s")).thenReturn(Optional.of(storedKey));

        assertNull(service.resolveKey(null));
        assertNull(service.resolveKey("short"));
        assertNull(service.resolveKey("md_missing-secret"));
        assertNull(service.resolveKey("md_valid-wrong"));

        verify(apiKeyIssuanceService, never()).pruneInvalidContexts(storedKey);
        verify(apiKeyRepository, never()).save(storedKey);
    }

    @Test
    void resolveKeyPrunesValidContextsAndUpdatesLastUsed() {
        String plainKey = "md_valid-secret";
        ApiKey storedKey = new ApiKey("user-1", "CI", new BCryptPasswordEncoder().encode(plainKey), plainKey.substring(0, 10));
        when(apiKeyRepository.findByPrefix(plainKey.substring(0, 10))).thenReturn(Optional.of(storedKey));

        ApiKey resolved = service.resolveKey(plainKey);

        assertSame(storedKey, resolved);
        assertNotNull(storedKey.getLastUsed());
        verify(apiKeyIssuanceService).pruneInvalidContexts(storedKey);
        verify(apiKeyRepository).save(storedKey);
    }

    @Test
    void resolveKeyNormalizesWhitespaceAndCommonHeaderSchemes() {
        String plainKey = "md_valid-secret";
        ApiKey storedKey = new ApiKey("user-1", "CI", new BCryptPasswordEncoder().encode(plainKey), plainKey.substring(0, 10));
        when(apiKeyRepository.findByPrefix(plainKey.substring(0, 10))).thenReturn(Optional.of(storedKey));

        assertSame(storedKey, service.resolveKey("  " + plainKey + "  "));
        assertSame(storedKey, service.resolveKey("Bearer " + plainKey));
        assertSame(storedKey, service.resolveKey("ApiKey " + plainKey));
    }

    @Test
    void getMyKeysAndGetUserFromKeyDelegateToRepositories() {
        ApiKey key = new ApiKey("user-1", "CI", "hash", "prefix");
        User user = new User();
        user.setId("user-1");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(apiKeyRepository.findByUserId("user-1")).thenReturn(List.of(key));

        assertSame(user, service.getUserFromKey(key));
        assertEquals(List.of(key), service.getMyKeys("user-1"));
    }

    @Test
    void revokeKeyDeletesOwnedKeysAndRejectsMissingOrForeignKeys() {
        ApiKey owned = new ApiKey("user-1", "CI", "hash", "prefix");
        ApiKey foreign = new ApiKey("other-user", "CI", "hash", "prefix");

        when(apiKeyRepository.findById("owned")).thenReturn(Optional.of(owned));
        when(apiKeyRepository.findById("foreign")).thenReturn(Optional.of(foreign));
        when(apiKeyRepository.findById("missing")).thenReturn(Optional.empty());

        service.revokeKey("owned", "user-1");

        verify(apiKeyRepository).delete(owned);
        assertThrows(ResourceNotFoundException.class, () -> service.revokeKey("missing", "user-1"));
        assertThrows(ApiKeyOperationForbiddenException.class, () -> service.revokeKey("foreign", "user-1"));
    }
}
