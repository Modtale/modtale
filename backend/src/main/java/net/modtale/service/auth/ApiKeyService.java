package net.modtale.service.auth;

import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

@Service
public class ApiKeyService {

    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserService userService;

    @Qualifier("taskExecutor")
    @Autowired private Executor taskExecutor;

    @Value("${app.limits.max-api-keys-per-user:10}")
    private int maxApiKeys;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public String createApiKey(String userId, String name, Map<String, Set<ApiKey.ApiPermission>> requestedContexts) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<ApiKey> existingKeys = apiKeyRepository.findByUserId(userId);
        if (existingKeys.size() >= maxApiKeys) {
            throw new IllegalStateException("You have reached the maximum limit of " + maxApiKeys + " API keys.");
        }

        List<User> userOrgs = userService.getUserOrganizations(userId);
        Map<String, Set<ApiKey.ApiPermission>> validatedContexts = new HashMap<>();

        for (Map.Entry<String, Set<ApiKey.ApiPermission>> entry : requestedContexts.entrySet()) {
            String contextId = entry.getKey();
            Set<ApiKey.ApiPermission> requestedPerms = entry.getValue();

            if ("PERSONAL".equals(contextId)) {
                validatedContexts.put(contextId, requestedPerms);
            } else {
                User org = userOrgs.stream()
                        .filter(o -> o.getId().equals(contextId))
                        .findFirst()
                        .orElseThrow(() -> new SecurityException("You are not a member of organization: " + contextId));

                User.OrganizationMember membership = org.getOrganizationMembers().stream()
                        .filter(m -> m.getUserId().equals(userId))
                        .findFirst()
                        .orElseThrow(() -> new SecurityException("Membership details not found."));

                Set<ApiKey.ApiPermission> allowedPerms = new HashSet<>();
                for (ApiKey.ApiPermission perm : requestedPerms) {
                    if ("ADMIN".equals(membership.getRole())) {
                        allowedPerms.add(perm);
                    } else {
                        if (!perm.name().contains("ORG_DELETE") &&
                                !perm.name().contains("ORG_EDIT_") &&
                                !perm.name().contains("ORG_MEMBER_")) {
                            allowedPerms.add(perm);
                        }
                    }
                }
                if (!allowedPerms.isEmpty()) {
                    validatedContexts.put(contextId, allowedPerms);
                }
            }
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String plainKey = "md_" + randomPart;
        String prefix = plainKey.substring(0, 10);

        ApiKey apiKey = new ApiKey(userId, name, encoder.encode(plainKey), prefix);
        apiKey.setTier(user.getTier());
        apiKey.setContextPermissions(validatedContexts);

        apiKeyRepository.save(apiKey);
        return plainKey;
    }

    public ApiKey resolveKey(String plainKey) {
        if (plainKey == null || plainKey.length() < 10) return null;

        String prefix = plainKey.substring(0, 10);

        ApiKey apiKey = apiKeyRepository.findByPrefix(prefix).orElse(null);
        if (apiKey == null) return null;

        if (encoder.matches(plainKey, apiKey.getKeyHash())) {
            updateLastUsed(apiKey);
            return apiKey;
        }

        return null;
    }

    private void updateLastUsed(ApiKey key) {
        taskExecutor.execute(() -> {
            key.setLastUsed(LocalDateTime.now());
            apiKeyRepository.save(key);
        });
    }

    public User getUserFromKey(ApiKey key) {
        return userRepository.findById(key.getUserId()).orElse(null);
    }

    public List<ApiKey> getMyKeys(String userId) {
        return apiKeyRepository.findByUserId(userId);
    }

    public void revokeKey(String keyId, String userId) {
        ApiKey key = apiKeyRepository.findById(keyId).orElse(null);
        if (key != null && key.getUserId().equals(userId)) {
            apiKeyRepository.delete(key);
        }
    }
}