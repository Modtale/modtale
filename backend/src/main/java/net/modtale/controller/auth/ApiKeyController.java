package net.modtale.controller.auth;

import jakarta.validation.Valid;
import java.util.List;
import net.modtale.mapper.AuthMapper;
import net.modtale.model.dto.auth.ApiKeyDTO;
import net.modtale.model.dto.request.auth.CreateApiKeyRequest;
import net.modtale.model.dto.response.auth.ApiKeySecretResponse;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.service.auth.ApiKeyService;
import net.modtale.service.user.account.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final AccountService accountService;

    public ApiKeyController(ApiKeyService apiKeyService, AccountService accountService) {
        this.apiKeyService = apiKeyService;
        this.accountService = accountService;
    }

    @GetMapping
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<List<ApiKeyDTO>> getMyKeys() {
        User user = accountService.requireCurrentUser("viewing your API keys");
        List<ApiKey> keys = apiKeyService.getMyKeys(user.getId());
        return ResponseEntity.ok(keys.stream().map(AuthMapper::toApiKeyDTO).toList());
    }

    @PostMapping
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<ApiKeySecretResponse> createKey(@Valid @RequestBody CreateApiKeyRequest payload) {
        User user = accountService.requireCurrentUser("creating an API key");
        if (!user.isEmailVerified()) {
            throw new SecurityException("Verify your email address before creating an API key.");
        }
        String rawKey = apiKeyService.createApiKey(user.getId(), payload.getName(), payload.getContextPermissions());
        return ResponseEntity.ok(new ApiKeySecretResponse(rawKey));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<Void> revokeKey(@PathVariable String id) {
        User user = accountService.requireCurrentUser("revoking an API key");
        apiKeyService.revokeKey(id, user.getId());
        return ResponseEntity.ok().build();
    }
}
