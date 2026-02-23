package net.modtale.service.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
public class ApiKeyService {

    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private UserRepository userRepository;
    @Qualifier("taskExecutor")
    @Autowired private Executor taskExecutor;

    @Value("${app.limits.max-api-keys-per-user:10}")
    private int maxApiKeys;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    private final Cache<String, ApiKey> keyCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    public String createApiKey(String userId, String name) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<ApiKey> existingKeys = apiKeyRepository.findByUserId(userId);
        if (existingKeys.size() >= maxApiKeys) {
            throw new IllegalStateException("You have reached the maximum limit of " + maxApiKeys + " API keys.");
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String plainKey = "md_" + randomPart;
        String prefix = plainKey.substring(0, 10);

        ApiKey apiKey = new ApiKey(
                userId,
                name,
                encoder.encode(plainKey),
                prefix
        );

        apiKey.setTier(user.getTier());

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