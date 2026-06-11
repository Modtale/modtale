package net.modtale.controller.auth;

import net.modtale.model.dto.request.auth.CreateApiKeyRequest;
import net.modtale.model.user.User;
import net.modtale.service.auth.ApiKeyService;
import net.modtale.service.user.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyControllerTest {

    private ApiKeyController controller;
    private ApiKeyService apiKeyService;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        apiKeyService = mock(ApiKeyService.class);
        accountService = mock(AccountService.class);
        controller = new ApiKeyController(apiKeyService, accountService);
    }

    @Test
    void createKeyReturnsTheGeneratedSecretForVerifiedUsers() {
        User user = new User();
        user.setId("user-1");
        user.setEmailVerified(true);

        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName("deploy");
        request.setContextPermissions(Map.of("PERSONAL", Set.of(net.modtale.model.user.ApiKey.ApiPermission.PROJECT_READ)));

        when(accountService.requireCurrentUser("creating an API key")).thenReturn(user);
        when(apiKeyService.createApiKey("user-1", "deploy", request.getContextPermissions())).thenReturn("md_secret");

        var response = controller.createKey(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("md_secret", response.getBody().key());
        verify(apiKeyService).createApiKey("user-1", "deploy", request.getContextPermissions());
    }

    @Test
    void createKeyRejectsUnverifiedUsers() {
        User user = new User();
        user.setId("user-1");
        user.setEmailVerified(false);

        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName("deploy");

        when(accountService.requireCurrentUser("creating an API key")).thenReturn(user);

        assertThrows(SecurityException.class, () -> controller.createKey(request));
    }
}
