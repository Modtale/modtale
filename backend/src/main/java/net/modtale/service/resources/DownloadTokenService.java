package net.modtale.service.resources;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DownloadTokenService {

    private static final int TOKEN_VALIDITY_MINUTES = 5;
    private static final int TOKEN_LENGTH = 32;

    private final Map<String, DownloadToken> tokens = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public static class DownloadToken {
        private final String projectId;
        private final String version;
        private final Instant expiresAt;
        private boolean used;

        public DownloadToken(String projectId, String version, Instant expiresAt) {
            this.projectId = projectId;
            this.version = version;
            this.expiresAt = expiresAt;
            this.used = false;
        }

        public String getProjectId() { return projectId; }
        public String getVersion() { return version; }
        public Instant getExpiresAt() { return expiresAt; }
        public boolean isUsed() { return used; }
        public void markAsUsed() { this.used = true; }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public String generateToken(String projectId, String version) {
        cleanExpiredTokens();

        byte[] randomBytes = new byte[TOKEN_LENGTH];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        Instant expiresAt = Instant.now().plusSeconds(TOKEN_VALIDITY_MINUTES * 60);
        tokens.put(token, new DownloadToken(projectId, version, expiresAt));

        return token;
    }

    public DownloadToken validateAndConsume(String token) {
        DownloadToken downloadToken = tokens.get(token);

        if (downloadToken == null) {
            return null;
        }

        if (downloadToken.isExpired()) {
            tokens.remove(token);
            return null;
        }

        if (downloadToken.isUsed()) {
            return null;
        }

        downloadToken.markAsUsed();
        tokens.remove(token);

        return downloadToken;
    }

    private void cleanExpiredTokens() {
        tokens.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public int getActiveTokenCount() {
        cleanExpiredTokens();
        return tokens.size();
    }
}
