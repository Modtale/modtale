package net.modtale.controller.auth;

import net.modtale.exception.ErrorMessageUtils;
import net.modtale.mapper.AuthMapper;
import net.modtale.model.dto.auth.ApiKeyDTO;
import net.modtale.model.dto.request.auth.CreateApiKeyRequest;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.service.user.AccountService;
import net.modtale.service.auth.ApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/user/api-keys")
public class ApiKeyController {

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private AccountService accountService;

    @GetMapping
    public ResponseEntity<List<ApiKeyDTO>> getMyKeys() {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<ApiKey> keys = apiKeyService.getMyKeys(user.getId());
        return ResponseEntity.ok(keys.stream().map(AuthMapper::toApiKeyDTO).toList());
    }

    @PostMapping
    public ResponseEntity<?> createKey(@RequestBody CreateApiKeyRequest payload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before creating an API key.");

        if (!user.isEmailVerified()) {
            return ErrorMessageUtils.forbidden("Verify your email address before creating an API key.");
        }

        String name = payload.getName();
        if (name == null || name.isBlank()) {
            return ErrorMessageUtils.badRequest("An API key name is required before we can create the key.");
        }

        try {
            String rawKey = apiKeyService.createApiKey(user.getId(), name, payload.getContextPermissions());
            return ResponseEntity.ok(Map.of("key", rawKey));
        } catch (SecurityException e) {
            return ErrorMessageUtils.forbidden(e, "You do not have permission to create an API key with those scopes.");
        } catch (IllegalStateException e) {
            return ErrorMessageUtils.badRequest(e, "We could not create that API key.");
        } catch (Exception e) {
            return ErrorMessageUtils.internalServerError(e, "Failed to create API key.");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> revokeKey(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before revoking an API key.");

        try {
            apiKeyService.revokeKey(id, user.getId());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ErrorMessageUtils.badRequest(e, "We could not revoke that API key.");
        }
    }
}
