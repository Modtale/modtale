package net.modtale.controller.auth;

import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.service.user.UserService;
import net.modtale.service.auth.ApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/user/api-keys")
public class ApiKeyController {

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private UserService userService;

    public static class CreateKeyRequest {
        private String name;
        private Set<ApiKey.ApiPermission> permissions;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Set<ApiKey.ApiPermission> getPermissions() { return permissions; }
        public void setPermissions(Set<ApiKey.ApiPermission> permissions) { this.permissions = permissions; }
    }

    @GetMapping
    public ResponseEntity<List<ApiKey>> getMyKeys() {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<ApiKey> keys = apiKeyService.getMyKeys(user.getId());

        keys.forEach(k -> k.setKeyHash(null));

        return ResponseEntity.ok(keys);
    }

    @PostMapping
    public ResponseEntity<?> createKey(@RequestBody CreateKeyRequest payload) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (!user.isEmailVerified()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Email verification required."));
        }

        String name = payload.getName();
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String rawKey = apiKeyService.createApiKey(user.getId(), name, payload.getPermissions());

        return ResponseEntity.ok(Map.of("key", rawKey));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeKey(@PathVariable String id) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        apiKeyService.revokeKey(id, user.getId());
        return ResponseEntity.ok().build();
    }
}