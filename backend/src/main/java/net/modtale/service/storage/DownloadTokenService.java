package net.modtale.service.storage;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class DownloadTokenService {

    private static final int TOKEN_VALIDITY_MINUTES = 5;
    private static final int TOKEN_LENGTH = 32;

    private final Map<String, DownloadToken> tokens = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public static class DownloadToken {
        private final String projectId;
        private final String version;
        private final String gameVersion;
        private final Instant expiresAt;
        private final List<String> selectedDependencies;
        private boolean used;

        public DownloadToken(String projectId, String version, String gameVersion, List<String> selectedDependencies, Instant expiresAt) {
            this.projectId = projectId;
            this.version = version;
            this.gameVersion = gameVersion;
            this.selectedDependencies = selectedDependencies;
            this.expiresAt = expiresAt;
            this.used = false;
        }

        public String getProjectId() { return projectId; }
        public String getVersion() { return version; }
        public String getGameVersion() { return gameVersion; }
        public List<String> getSelectedDependencies() { return selectedDependencies; }
        public Instant getExpiresAt() { return expiresAt; }
        public boolean isUsed() { return used; }
        public void markAsUsed() { this.used = true; }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public String generateToken(String projectId, String version, String gameVersion, List<String> selectedDependencies) {
        cleanExpiredTokens();

        byte[] randomBytes = new byte[TOKEN_LENGTH];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        Instant expiresAt = Instant.now().plusSeconds(TOKEN_VALIDITY_MINUTES * 60);
        tokens.put(token, new DownloadToken(projectId, version, gameVersion, selectedDependencies, expiresAt));

        return token;
    }

    public String generateToken(String projectId, String version, String gameVersion) {
        return generateToken(projectId, version, gameVersion, null);
    }

    public String generateToken(String projectId, String version) {
        return generateToken(projectId, version, null, null);
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

    public int getTokenValiditySeconds() {
        return TOKEN_VALIDITY_MINUTES * 60;
    }
}
