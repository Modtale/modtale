package net.modtale.controller.auth;

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
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (!user.isEmailVerified()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Email verification required."));
        }

        String name = payload.getName();
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            String rawKey = apiKeyService.createApiKey(user.getId(), name, payload.getContextPermissions());
            return ResponseEntity.ok(Map.of("key", rawKey));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeKey(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        apiKeyService.revokeKey(id, user.getId());
        return ResponseEntity.ok().build();
    }
}
