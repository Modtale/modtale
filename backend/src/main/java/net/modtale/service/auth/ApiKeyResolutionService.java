package net.modtale.service.auth;

import net.modtale.exception.ApiKeyOperationForbiddenException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

@Service
public class ApiKeyResolutionService {

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final ApiKeyIssuanceService apiKeyIssuanceService;
    private final Executor taskExecutor;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public ApiKeyResolutionService(
            ApiKeyRepository apiKeyRepository,
            UserRepository userRepository,
            ApiKeyIssuanceService apiKeyIssuanceService,
            @Qualifier("taskExecutor") Executor taskExecutor
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
        this.apiKeyIssuanceService = apiKeyIssuanceService;
        this.taskExecutor = taskExecutor;
    }

    public ApiKey resolveKey(String plainKey) {
        if (plainKey == null || plainKey.length() < 10) {
            return null;
        }

        String prefix = plainKey.substring(0, 10);
        ApiKey apiKey = apiKeyRepository.findByPrefix(prefix).orElse(null);
        if (apiKey == null) {
            return null;
        }

        if (!encoder.matches(plainKey, apiKey.getKeyHash())) {
            return null;
        }

        apiKeyIssuanceService.pruneInvalidContexts(apiKey);
        updateLastUsed(apiKey);
        return apiKey;
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
        } else if (key == null) {
            throw new ResourceNotFoundException("We couldn't find that API key.");
        } else {
            throw new ApiKeyOperationForbiddenException("You do not have permission to revoke that API key.");
        }
    }

    private void updateLastUsed(ApiKey key) {
        taskExecutor.execute(() -> {
            key.setLastUsed(LocalDateTime.now());
            apiKeyRepository.save(key);
        });
    }
}
