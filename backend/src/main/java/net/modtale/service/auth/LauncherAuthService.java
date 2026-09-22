package net.modtale.service.auth;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.modtale.exception.InvalidAuthenticationRequestException;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class LauncherAuthService {

    public static final String OAUTH_REDIRECT_URI_SESSION_ATTRIBUTE = "MODTALE_LAUNCHER_OAUTH_REDIRECT_URI";
    public static final String OAUTH_STATE_SESSION_ATTRIBUTE = "MODTALE_LAUNCHER_OAUTH_STATE";
    private static final Duration CODE_VALIDITY = Duration.ofMinutes(5);
    private static final int CODE_LENGTH_BYTES = 32;

    private final Map<String, LauncherAuthCode> codes = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final UserRepository userRepository;

    public LauncherAuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public LauncherAuthGrant issueCode(User user, String redirectUri, String state) {
        if (user == null || user.getId() == null || user.getId().isBlank()) {
            throw new InvalidAuthenticationRequestException("You need to sign in before authorizing the Modtale Launcher.");
        }
        validateLoopbackRedirectUri(redirectUri);
        cleanExpiredCodes();

        byte[] randomBytes = new byte[CODE_LENGTH_BYTES];
        secureRandom.nextBytes(randomBytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Instant expiresAt = Instant.now().plus(CODE_VALIDITY);
        codes.put(code, new LauncherAuthCode(user.getId(), expiresAt));

        return new LauncherAuthGrant(code, redirectUri, normalizeState(state), Math.toIntExact(CODE_VALIDITY.toSeconds()));
    }

    public User consumeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        LauncherAuthCode authCode = codes.remove(code);
        if (authCode == null || authCode.isExpired()) {
            return null;
        }

        Optional<User> user = userRepository.findById(authCode.userId());
        return user.filter(candidate -> !candidate.isDeleted()).orElse(null);
    }

    public int getActiveCodeCount() {
        cleanExpiredCodes();
        return codes.size();
    }

    public void validateLoopbackRedirectUri(String redirectUri) {
        URI uri;
        try {
            uri = URI.create(redirectUri == null ? "" : redirectUri.trim());
        } catch (IllegalArgumentException ex) {
            throw new InvalidAuthenticationRequestException("The launcher callback URL is invalid.");
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"http".equalsIgnoreCase(scheme) || host == null || uri.getPort() < 1) {
            throw new InvalidAuthenticationRequestException("The launcher callback URL must use a local HTTP callback.");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        boolean loopback = "localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost)
                || "[::1]".equals(normalizedHost);
        if (!loopback) {
            throw new InvalidAuthenticationRequestException("The launcher callback URL must point to this device.");
        }
    }

    private static String normalizeState(String state) {
        return state == null ? "" : state.trim();
    }

    private void cleanExpiredCodes() {
        codes.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public record LauncherAuthGrant(String code, String redirectUri, String state, int expiresIn) {
    }

    private record LauncherAuthCode(String userId, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
